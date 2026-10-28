package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.error.InvalidOAuthStateException;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2StateData;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2StateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production implementation of OAuth2StateService.
 * Manages PKCE state tokens with 10-minute TTL, single-use consumption,
 * Redis persistence with resilient in-memory fallback, and RFC 7636 S256 verification.
 */
@Service
public class OAuth2StateServiceImpl implements OAuth2StateService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2StateServiceImpl.class);
    private static final String REDIS_PREFIX = "oauth2:state:";
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTE_LENGTH = 32; // 256 bits of entropy
    private static final String DELIMITER = "\u001F"; // Unit separator ASCII 31

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final SecureRandom secureRandom;
    private final Base64.Encoder urlEncoder;
    private final Map<String, CachedState> inMemoryCache = new ConcurrentHashMap<>();

    private record CachedState(OAuth2StateData data, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public OAuth2StateServiceImpl(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.secureRandom = new SecureRandom();
        this.urlEncoder = Base64.getUrlEncoder().withoutPadding();
    }

    @Override
    public String generateAndStoreState(String codeChallenge, String redirectUri) {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("codeChallenge must not be null or blank");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be null or blank");
        }

        byte[] randomBytes = new byte[STATE_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String state = urlEncoder.encodeToString(randomBytes);

        OAuth2StateData stateData = new OAuth2StateData(codeChallenge.trim(), redirectUri.trim(), Instant.now());
        Instant expiresAt = Instant.now().plus(STATE_TTL);

        // Always store in local fallback cache
        inMemoryCache.put(state, new CachedState(stateData, expiresAt));

        // Store in Redis if available
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate != null) {
            try {
                String serialized = stateData.codeChallenge() + DELIMITER + stateData.redirectUri();
                redisTemplate.opsForValue().set(REDIS_PREFIX + state, serialized, STATE_TTL);
            } catch (Exception e) {
                log.warn("Failed to store OAuth state in Redis, falling back to local memory: {}", e.getMessage());
            }
        }

        pruneExpiredLocalStates();
        return state;
    }

    @Override
    public OAuth2StateData consumeState(String state) {
        if (state == null || state.isBlank()) {
            throw new InvalidOAuthStateException("OAuth state is missing or empty");
        }

        String trimmedState = state.trim();
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();

        // 1. Try Redis first (distributed atomic single-use consumption)
        if (redisTemplate != null) {
            try {
                String serialized = redisTemplate.opsForValue().getAndDelete(REDIS_PREFIX + trimmedState);
                if (serialized != null && serialized.contains(DELIMITER)) {
                    String[] parts = serialized.split(DELIMITER, 2);
                    inMemoryCache.remove(trimmedState);
                    return new OAuth2StateData(parts[0], parts[1], Instant.now());
                }
            } catch (Exception e) {
                log.warn("Failed to consume OAuth state from Redis, checking local cache: {}", e.getMessage());
            }
        }

        // 2. Try in-memory fallback cache (atomic single-use remove)
        CachedState cached = inMemoryCache.remove(trimmedState);
        if (cached != null && !cached.isExpired()) {
            return cached.data();
        }

        log.warn("OAuth2 state consumption rejected: state does not exist or has expired [state={}]", maskState(trimmedState));
        throw new InvalidOAuthStateException("OAuth state is invalid or has expired");
    }

    @Override
    public boolean validatePkce(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeVerifier.isBlank() || codeChallenge == null || codeChallenge.isBlank()) {
            return false;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.trim().getBytes(StandardCharsets.US_ASCII));
            String computedChallenge = urlEncoder.encodeToString(hash);

            return MessageDigest.isEqual(
                    computedChallenge.getBytes(StandardCharsets.UTF_8),
                    codeChallenge.trim().getBytes(StandardCharsets.UTF_8)
            );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable for PKCE verification", e);
        }
    }

    private void pruneExpiredLocalStates() {
        if (inMemoryCache.size() > 500) {
            inMemoryCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }

    private String maskState(String state) {
        if (state.length() <= 8) {
            return "***";
        }
        return state.substring(0, 4) + "..." + state.substring(state.length() - 4);
    }
}

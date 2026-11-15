package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenGenerator;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Production implementation of RefreshTokenGenerator.
 * Generates 256-bit URL-safe tokens and computes SHA-256 digests using HexFormat.
 */
@Service
public class RefreshTokenGeneratorImpl implements RefreshTokenGenerator {

    private static final int TOKEN_BYTE_LENGTH = 32; // 256 bits of entropy
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom;
    private final Base64.Encoder urlEncoder;
    private final JwtProperties jwtProperties;

    public RefreshTokenGeneratorImpl(JwtProperties jwtProperties) {
        this.jwtProperties = Objects.requireNonNull(jwtProperties, "JwtProperties must not be null");
        this.secureRandom = new SecureRandom();
        this.urlEncoder = Base64.getUrlEncoder().withoutPadding();
    }

    @Override
    public String generateToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return urlEncoder.encodeToString(randomBytes);
    }

    @Override
    public String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Raw refresh token must not be null or blank");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in current JVM", e);
        }
    }

    @Override
    public RefreshToken createRefreshToken(User user, String rawToken) {
        return createRefreshToken(user, rawToken, Duration.ofSeconds(jwtProperties.refreshTokenTtlSeconds()));
    }

    @Override
    public RefreshToken createRefreshToken(User user, String rawToken, Duration ttl) {
        Objects.requireNonNull(user, "User must not be null");
        Objects.requireNonNull(ttl, "TTL must not be null");

        String hash = hashToken(rawToken);
        Instant expiresAt = Instant.now().plus(ttl);
        return new RefreshToken(user, hash, expiresAt);
    }
}

package lk.ac.mrt.swiftdrop.merchant_service.service;

import static lk.ac.mrt.swiftdrop.merchant_service.api.ApiKeyDtos.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.merchant_service.domain.ApiKey;
import lk.ac.mrt.swiftdrop.merchant_service.domain.Merchant;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;
import lk.ac.mrt.swiftdrop.merchant_service.repository.ApiKeyRepository;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApiKeyService {
    public static final Set<String> ALLOWED_SCOPES = Set.of("parcels:read", "parcels:write", "webhooks:read", "webhooks:write");
    private final ApiKeyRepository keys;
    private final MerchantRepository merchants;
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public ApiKeyService(ApiKeyRepository keys, MerchantRepository merchants) {
        this(keys, merchants, Clock.systemUTC());
    }

    ApiKeyService(ApiKeyRepository keys, MerchantRepository merchants, Clock clock) {
        this.keys = keys; this.merchants = merchants; this.clock = clock;
    }

    @Transactional
    public Created create(UUID ownerId, CreateRequest request) {
        Merchant merchant = ownedApproved(ownerId);
        if (!ALLOWED_SCOPES.containsAll(request.scopes())) throw new IllegalArgumentException("Unsupported API key scope");
        if (request.expiresAt() != null && !request.expiresAt().isAfter(clock.instant())) throw new IllegalArgumentException("Expiry must be in the future");
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String plaintext = "sdk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String prefix = plaintext.substring(0, 8);
        ApiKey key = keys.save(ApiKey.builder().merchant(merchant).keyHash(hash(plaintext)).keyPrefix(prefix)
                .label(request.label().trim()).scopes(request.scopes().toArray(String[]::new)).expiresAt(request.expiresAt()).build());
        return new Created(key.getId(), plaintext, prefix, key.getLabel(), Set.of(key.getScopes()), key.getExpiresAt(), key.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<View> list(UUID ownerId) {
        Merchant merchant = ownedApproved(ownerId);
        return keys.findAllByMerchantIdOrderByCreatedAtDesc(merchant.getId()).stream().map(ApiKeyService::view).toList();
    }

    @Transactional
    public View revoke(UUID ownerId, UUID keyId) {
        Merchant merchant = ownedApproved(ownerId);
        ApiKey key = keys.findByIdAndMerchantId(keyId, merchant.getId()).orElseThrow(() -> new IllegalArgumentException("API key not found"));
        if (key.getRevokedAt() == null) key.setRevokedAt(clock.instant());
        return view(keys.save(key));
    }

    @Transactional
    public ApiKey verify(String plaintext, String requiredScope) {
        if (plaintext == null || plaintext.length() < 8 || !ALLOWED_SCOPES.contains(requiredScope)) throw new SecurityException("Invalid API key");
        byte[] candidate = hash(plaintext).getBytes(StandardCharsets.US_ASCII);
        Instant now = clock.instant();
        return keys.findAllByKeyPrefixAndRevokedAtIsNull(plaintext.substring(0, 8)).stream()
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(now))
                .filter(k -> Set.of(k.getScopes()).contains(requiredScope))
                .filter(k -> MessageDigest.isEqual(candidate, k.getKeyHash().getBytes(StandardCharsets.US_ASCII)))
                .findFirst().map(k -> { k.setLastUsedAt(now); return keys.save(k); })
                .orElseThrow(() -> new SecurityException("Invalid API key or scope"));
    }

    private Merchant ownedApproved(UUID ownerId) {
        Merchant m = merchants.findByOwnerUserId(ownerId).orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        if (m.getStatus() != MerchantStatus.APPROVED) throw new IllegalStateException("Merchant is not approved");
        return m;
    }
    private static View view(ApiKey k) { return new View(k.getId(), k.getKeyPrefix(), k.getLabel(), Set.of(k.getScopes()), k.getLastUsedAt(), k.getRevokedAt(), k.getExpiresAt(), k.getCreatedAt()); }
    private static String hash(String value) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

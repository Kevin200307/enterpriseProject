package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuration properties for JWT issuance, token lifecycle, and cryptographic RSA keys.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        String privateKeyPem,
        String publicKeyPem,
        String keyId
) {
    @ConstructorBinding
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "http://identity-service:8080";
        }
        if (audience == null || audience.isBlank()) {
            audience = "swiftdrop";
        }
        if (accessTokenTtlSeconds <= 0) {
            accessTokenTtlSeconds = 900; // 15 minutes
        }
        if (refreshTokenTtlSeconds <= 0) {
            refreshTokenTtlSeconds = 604800; // 7 days
        }
        if (keyId == null || keyId.isBlank()) {
            keyId = "swiftdrop-identity-key-1";
        }
    }

    /**
     * Backward-compatible constructor for components/tests specifying 6 arguments without audience.
     */
    public JwtProperties(
            String issuer,
            long accessTokenTtlSeconds,
            long refreshTokenTtlSeconds,
            String privateKeyPem,
            String publicKeyPem,
            String keyId
    ) {
        this(issuer, "swiftdrop", accessTokenTtlSeconds, refreshTokenTtlSeconds, privateKeyPem, publicKeyPem, keyId);
    }

    /**
     * Backward-compatible constructor for components/tests specifying 6 arguments with audience.
     */
    public JwtProperties(
            String issuer,
            String audience,
            long accessTokenTtlSeconds,
            String keyId,
            String privateKeyPem,
            String publicKeyPem
    ) {
        this(issuer, audience, accessTokenTtlSeconds, 604800, privateKeyPem, publicKeyPem, keyId);
    }
}


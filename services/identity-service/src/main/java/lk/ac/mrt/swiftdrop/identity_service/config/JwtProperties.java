package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for JWT issuance, token lifecycle, and cryptographic RSA keys.
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        long accessTokenTtlSeconds,
        long refreshTokenTtlSeconds,
        String privateKeyPem,
        String publicKeyPem,
        String keyId
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "http://identity-service:8080";
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
}

package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        String issuer,
        String audience,
        long accessTokenTtlSeconds,
        String keyId,
        String privateKeyPem,
        String publicKeyPem) {
}

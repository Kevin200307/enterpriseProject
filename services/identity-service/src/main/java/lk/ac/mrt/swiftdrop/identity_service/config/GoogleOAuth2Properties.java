package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Google OAuth 2.0 / OIDC sign-in flow 
 * Loaded from 'app.security.oauth2.google' in application.yaml with environment variable overrides.
 */
@ConfigurationProperties(prefix = "app.security.oauth2.google")
public record GoogleOAuth2Properties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String jwkSetUri,
        String issuer
) {
    public GoogleOAuth2Properties {
        if (clientId == null || clientId.isBlank()) {
            clientId = "mock-google-client-id.apps.googleusercontent.com";
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            clientSecret = "mock-google-client-secret";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            redirectUri = "http://localhost:5173/oauth/callback";
        }
        if (authorizationUri == null || authorizationUri.isBlank()) {
            authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth";
        }
        if (tokenUri == null || tokenUri.isBlank()) {
            tokenUri = "https://oauth2.googleapis.com/token";
        }
        if (userInfoUri == null || userInfoUri.isBlank()) {
            userInfoUri = "https://openidconnect.googleapis.com/v1/userinfo";
        }
        if (jwkSetUri == null || jwkSetUri.isBlank()) {
            jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "https://accounts.google.com";
        }
    }
}

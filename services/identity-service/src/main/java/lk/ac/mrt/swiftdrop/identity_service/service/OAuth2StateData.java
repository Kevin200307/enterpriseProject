package lk.ac.mrt.swiftdrop.identity_service.service;

import java.time.Instant;

/**
 * Data payload stored for an active OAuth 2.0 PKCE authorization session 
 * Links the cryptographically generated state parameter to the PKCE code_challenge and redirect_uri.
 */
public record OAuth2StateData(
        String codeChallenge,
        String redirectUri,
        Instant createdAt
) {
    public OAuth2StateData {
        if (codeChallenge == null || codeChallenge.isBlank()) {
            throw new IllegalArgumentException("codeChallenge must not be null or blank");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be null or blank");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response payload containing the generated Google OAuth 2.0 authorization URL
 * and cryptographically secure state token 
 */
@Schema(description = "Response containing Google OAuth 2.0 authorization URL and state token")
public record GoogleAuthorizeResponse(
        @Schema(
                description = "Full Google OAuth 2.0 authorization URL with PKCE and state parameters",
                example = "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=...&state=...&code_challenge=...&code_challenge_method=S256"
        )
        String authorizationUrl,

        @Schema(
                description = "Cryptographically secure state token to protect against CSRF attacks",
                example = "m4sK_9jQ2..."
        )
        String state
) {
    public GoogleAuthorizeResponse {
        if (authorizationUrl == null || authorizationUrl.isBlank()) {
            throw new IllegalArgumentException("authorizationUrl must not be null or blank");
        }
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("state must not be null or blank");
        }
    }
}

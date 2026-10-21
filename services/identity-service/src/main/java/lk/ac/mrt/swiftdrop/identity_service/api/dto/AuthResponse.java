package lk.ac.mrt.swiftdrop.identity_service.api.dto;

/**
 * Authentication response payload containing the short-lived RS256 access token
 * and authenticated user profile. (Refresh tokens are transported via HttpOnly cookies).
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public AuthResponse(String accessToken, long expiresIn, UserResponse user) {
        this(accessToken, "Bearer", expiresIn, user);
    }
}

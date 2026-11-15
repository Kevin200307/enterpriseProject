package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;

import java.util.Objects;

/**
 * Result of a successful refresh token rotation operation.
 * Contains the new RS256 access token for the client response body
 * and the new raw refresh token for the HttpOnly cookie.
 */
public record TokenRotationResult(
        String accessToken,
        String rawRefreshToken,
        long expiresIn,
        User user
) {
    public TokenRotationResult {
        Objects.requireNonNull(accessToken, "accessToken must not be null");
        Objects.requireNonNull(rawRefreshToken, "rawRefreshToken must not be null");
        Objects.requireNonNull(user, "user must not be null");
    }

    /**
     * Converts this rotation result to an AuthResponse DTO suitable for REST responses.
     */
    public AuthResponse toAuthResponse() {
        return new AuthResponse(accessToken, expiresIn, UserResponse.fromEntity(user));
    }
}

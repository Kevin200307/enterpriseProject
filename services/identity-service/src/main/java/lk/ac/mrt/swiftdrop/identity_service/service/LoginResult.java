package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;

import java.util.Objects;

/**
 * Result of authentication containing the public AuthResponse DTO
 * and the raw opaque refresh token to be transported in an HttpOnly cookie (§4.3).
 */
public record LoginResult(
        AuthResponse authResponse,
        String rawRefreshToken
) {
    public LoginResult {
        Objects.requireNonNull(authResponse, "authResponse must not be null");
        Objects.requireNonNull(rawRefreshToken, "rawRefreshToken must not be null");
    }
}

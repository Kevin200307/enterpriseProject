package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.domain.User;

import java.util.UUID;

/**
 * Service managing refresh token lifecycle, atomic rotation,
 * and security replay mitigation (§4.3).
 */
public interface RefreshTokenService {

    /**
     * Issues a new refresh token for the user upon successful login.
     * Persists the SHA-256 hash in the database and returns the raw opaque token
     * for transport in an HttpOnly cookie.
     *
     * @param user the authenticated User entity
     * @return the raw opaque refresh token string
     */
    String issueRefreshToken(User user);

    /**
     * Rotates a refresh token:
     * 1. Validates the provided raw refresh token.
     * 2. Detects replay attacks: if the token is already revoked, revokes all tokens for the user and throws InvalidTokenException.
     * 3. Checks expiration: if expired, throws InvalidTokenException.
     * 4. Checks user status: if suspended, throws UserSuspendedException.
     * 5. Atomically revokes old token (setting replaced_by) and issues a new refresh token.
     * 6. Generates a new RS256 access token.
     *
     * @param rawRefreshToken the raw refresh token presented by client
     * @return TokenRotationResult containing new access token, new raw refresh token, and user
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException on invalid, expired, or replayed tokens
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException if the user account is suspended
     */
    TokenRotationResult rotate(String rawRefreshToken);

    /**
     * Revokes a specific refresh token (e.g. on single-device logout).
     *
     * @param rawRefreshToken the raw refresh token to revoke
     */
    void revokeToken(String rawRefreshToken);

    /**
     * Revokes all active refresh tokens for the given user ID (e.g. on global logout or password reset).
     *
     * @param userId the UUID of the user
     */
    void revokeAllForUser(UUID userId);
}

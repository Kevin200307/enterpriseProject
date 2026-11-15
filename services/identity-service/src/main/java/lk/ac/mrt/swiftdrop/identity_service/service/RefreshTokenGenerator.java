package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;

import java.time.Duration;

/**
 * Service generating cryptographically secure opaque refresh tokens
 * and computing their SHA-256 hashes for storage (§4.3).
 */
public interface RefreshTokenGenerator {

    /**
     * Generates a cryptographically strong, 256-bit (32-byte) random token,
     * encoded in URL-safe Base64 without padding.
     *
     * @return the raw opaque refresh token string
     */
    String generateToken();

    /**
     * Computes the SHA-256 hash of the given raw refresh token.
     *
     * @param rawToken the unhashed refresh token
     * @return 64-character lowercase hexadecimal hash string
     */
    String hashToken(String rawToken);

    /**
     * Creates a new RefreshToken entity for the specified user and raw token
     * using the configured refresh token TTL (7 days).
     *
     * @param user the owning user
     * @param rawToken the raw unhashed refresh token
     * @return initialized RefreshToken entity ready for persistence
     */
    RefreshToken createRefreshToken(User user, String rawToken);

    /**
     * Creates a new RefreshToken entity for the specified user and raw token
     * with an explicit custom TTL.
     *
     * @param user the owning user
     * @param rawToken the raw unhashed refresh token
     * @param ttl the duration until token expiry
     * @return initialized RefreshToken entity ready for persistence
     */
    RefreshToken createRefreshToken(User user, String rawToken, Duration ttl);
}

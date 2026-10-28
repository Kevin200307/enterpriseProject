package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.util.UUID;

/**
 * Service producing and validating RS256 signed JSON Web Tokens (JWT)
 * adhering strictly to the SwiftDrop Auth contract (§4.3).
 */
public interface JwtService {

    /**
     * Generates a signed RS256 access token for the given user with standard claims:
     * - iss: configured issuer URI
     * - sub: user UUID
     * - email: user email
     * - roles: array of user roles (CUSTOMER, MERCHANT, COURIER, DISPATCHER, ADMIN)
     * - exp: current time + 15 minutes (or configured access token TTL)
     *
     * @param user the authenticated User entity
     * @return the encoded RS256 JWT string
     */
    String generateAccessToken(User user);

    /**
     * Generates a signed RS256 access token for the given user, optionally including merchantId.
     *
     * @param user the authenticated User entity
     * @param merchantId optional merchant UUID (for MERCHANT users)
     * @return the encoded RS256 JWT string
     */
    String generateAccessToken(User user, UUID merchantId);

    /**
     * Validates and decodes an encoded JWT string.
     *
     * @param token the encoded JWT string
     * @return the decoded Jwt object containing headers and claims
     * @throws org.springframework.security.oauth2.jwt.JwtException if the token is invalid or expired
     */
    Jwt decodeToken(String token);

    /**
     * Returns the configured access token time-to-live.
     *
     * @return TTL duration (typically 15 minutes)
     */
    Duration getAccessTokenTtl();
}

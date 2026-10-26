package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;

/**
 * Authentication service handling credential verification, rate limit enforcement,
 * and anti-timing attack side-channel defenses.
 */
public interface AuthService {

    /**
     * Authenticates a user using email and password credentials.
     * Enforces rate limiting, constant-time dummy hash matching, and status checks.
     *
     * @param request the login credentials
     * @return the authenticated User entity
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.InvalidCredentialsException on wrong email or password
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException on suspended accounts
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException if rate limit exceeded
     */
    User authenticate(LoginRequest request);

    /**
     * Executes authentication and returns the sanitized UserResponse.
     *
     * @param request the login credentials
     * @return the authenticated user's profile
     */
    AuthResponse login(LoginRequest request);
}

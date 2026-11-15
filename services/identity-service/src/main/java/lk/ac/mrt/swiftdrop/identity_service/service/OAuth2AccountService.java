package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.domain.User;

/**
 * Service managing user provisioning and account linking for OAuth 2.0 / OIDC sign-ins.
 */
public interface OAuth2AccountService {

    /**
     * Resolves an authenticated Google identity to a local SwiftDrop user entity.
     * Matches on existing google_sub or verified email:
     * - If matching google_sub exists and email matches: returns existing user.
     * - If matching google_sub exists but email differs: throws ConflictException.
     * - If matching email exists without google_sub: links google_sub to existing account.
     * - If matching email exists with different google_sub: throws ConflictException.
     * - If no matching account exists: provisions a new active CUSTOMER with null password_hash.
     *
     * @param claims verified Google identity claims
     * @return the resolved, linked, or newly provisioned User entity
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.ConflictException if google_sub or email is bound to another user
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException if the linked account is suspended
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException if email is not verified
     */
    User linkOrProvisionGoogleUser(GoogleUserClaims claims);

    /**
     * Orchestrates Google authentication by linking or provisioning the user,
     * and generating SwiftDrop RS256 access token and opaque refresh token.
     *
     * @param claims verified Google identity claims
     * @return the login result containing AuthResponse and raw refresh token for cookie transport
     */
    LoginResult authenticateGoogleUser(GoogleUserClaims claims);
}

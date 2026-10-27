package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.ConflictException;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleUserClaims;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtService;
import lk.ac.mrt.swiftdrop.identity_service.service.LoginResult;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2AccountService;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Implementation of OAuth2AccountService handling Google OAuth2 account linking,
 * automatic user provisioning, and token issuance.
 */
@Service
@Transactional
public class OAuth2AccountServiceImpl implements OAuth2AccountService {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AccountServiceImpl.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public OAuth2AccountServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.roleRepository = Objects.requireNonNull(roleRepository, "roleRepository must not be null");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService must not be null");
        this.refreshTokenService = Objects.requireNonNull(refreshTokenService, "refreshTokenService must not be null");
    }

    @Override
    public User linkOrProvisionGoogleUser(GoogleUserClaims claims) {
        if (claims == null) {
            throw new IllegalArgumentException("GoogleUserClaims must not be null");
        }
        if (!claims.emailVerified()) {
            throw new InvalidTokenException("Google account email is not verified");
        }

        String normalizedEmail = claims.email().trim().toLowerCase(Locale.ROOT);
        String googleSub = claims.sub().trim();

        // 1. Look up existing user by Google subject ID (google_sub)
        Optional<User> userBySub = userRepository.findByGoogleSub(googleSub);
        if (userBySub.isPresent()) {
            User existingUser = userBySub.get();

            // Verify email consistency: if sub is linked to a different email, fail with 409 CONFLICT
            if (!existingUser.getEmail().equalsIgnoreCase(normalizedEmail)) {
                log.warn("OAuth linking conflict: google_sub already linked to a different email account [sub={}]", googleSub);
                throw new ConflictException("Google account is already linked to another user account");
            }

            // Verify account status
            if (existingUser.isSuspended()) {
                log.warn("OAuth sign-in rejected: account suspended [userId={}]", existingUser.getId());
                throw new UserSuspendedException();
            }

            log.info("User authenticated via existing Google OAuth link [userId={}, email={}]", existingUser.getId(), maskEmail(existingUser.getEmail()));
            return existingUser;
        }

        // 2. Look up existing local user by verified email
        Optional<User> userByEmail = userRepository.findByEmailIgnoreCase(normalizedEmail);
        if (userByEmail.isPresent()) {
            User localUser = userByEmail.get();

            // If account is already linked to a different Google account, fail with 409 CONFLICT
            if (localUser.getGoogleSub() != null && !localUser.getGoogleSub().equals(googleSub)) {
                log.warn("OAuth linking conflict: user account already linked to a different google_sub [userId={}]", localUser.getId());
                throw new ConflictException("User account is already linked to a different Google account");
            }

            // Verify account status
            if (localUser.isSuspended()) {
                log.warn("OAuth sign-in rejected: account suspended [userId={}]", localUser.getId());
                throw new UserSuspendedException();
            }

            // Link Google sub to existing local account
            localUser.setGoogleSub(googleSub);
            if (localUser.getFullName() == null || localUser.getFullName().isBlank()) {
                localUser.setFullName(claims.name() != null ? claims.name().trim() : normalizedEmail);
            }

            User saved = userRepository.save(localUser);
            log.info("Linked existing user account with Google OAuth [userId={}, email={}]", saved.getId(), maskEmail(saved.getEmail()));
            return saved;
        }

        // 3. Provision new user with CUSTOMER role and null password_hash
        RoleEntity customerRole = roleRepository.findByRole(Role.CUSTOMER)
                .orElseGet(() -> {
                    log.warn("CUSTOMER role not found in database; initializing default entity");
                    return roleRepository.save(new RoleEntity(Role.CUSTOMER, "Standard consumer role"));
                });

        User newUser = new User();
        newUser.setEmail(normalizedEmail);
        newUser.setPasswordHash(null); // Google-only users have null password_hash and are rejected by password login
        newUser.setFullName(claims.name() != null && !claims.name().isBlank() ? claims.name().trim() : normalizedEmail);
        newUser.setPhone(""); // Default empty phone string for OAuth provisioned accounts
        newUser.setGoogleSub(googleSub);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setMustChangePassword(false);
        newUser.addRole(customerRole);

        User saved = userRepository.save(newUser);
        log.info("Provisioned new user via Google OAuth [userId={}, email={}, role=CUSTOMER]", saved.getId(), maskEmail(saved.getEmail()));
        return saved;
    }

    @Override
    public LoginResult authenticateGoogleUser(GoogleUserClaims claims) {
        User user = linkOrProvisionGoogleUser(claims);
        String accessToken = jwtService.generateAccessToken(user);
        long expiresIn = jwtService.getAccessTokenTtl().toSeconds();
        String rawRefreshToken = refreshTokenService.issueRefreshToken(user);
        AuthResponse authResponse = new AuthResponse(accessToken, expiresIn, UserResponse.fromEntity(user));
        return new LoginResult(authResponse, rawRefreshToken);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf('@');
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}

package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidCredentialsException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.RateLimiterService;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtService;
import lk.ac.mrt.swiftdrop.identity_service.service.LoginResult;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Implementation of AuthService protecting password authentication with:
 * 1. Distributed rate limiting via Redis
 * 2. Constant-time dummy BCrypt execution to eliminate timing attack side-channels
 * 3. Consistent 401 INVALID_CREDENTIALS for both missing users and wrong passwords
 * 4. 403 ACCESS_DENIED for suspended accounts
 * 5. Short-lived RS256 access token issuance and long-lived refresh token generation
 */
@Service
@Transactional
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    // Pre-computed BCrypt 10 hash of "dummy" to equalize execution time for missing users
    private static final String DUMMY_BCRYPT_HASH = "$2b$10$e8kgnfD.V27B5eG8q4lDhe6K3KfZ9Wq6O0a3/JvHcqkC4b/9iV.gK";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RateLimiterService rateLimiterService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RateLimiterService rateLimiterService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rateLimiterService = rateLimiterService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public User authenticate(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);

        // 1. Check rate limit before executing intensive cryptographic hashing
        rateLimiterService.checkRateLimit(normalizedEmail);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCase(normalizedEmail);

        if (userOpt.isEmpty()) {
            // Anti-timing attack defense: execute BCrypt matching on dummy hash to neutralize side-channel timing analysis
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            rateLimiterService.recordFailedAttempt(normalizedEmail);
            log.warn("Authentication failed: user not found [email={}]", maskEmail(normalizedEmail));
            throw new InvalidCredentialsException();
        }

        User user = userOpt.get();

        // 2. Reject Google-only users without passwords (must use Google OAuth login)
        if (user.getPasswordHash() == null) {
            passwordEncoder.matches(request.password(), DUMMY_BCRYPT_HASH);
            rateLimiterService.recordFailedAttempt(normalizedEmail);
            log.warn("Authentication failed: user has no password configured (OAuth user) [email={}]", maskEmail(normalizedEmail));
            throw new InvalidCredentialsException();
        }

        // 3. Verify BCrypt password hash
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            rateLimiterService.recordFailedAttempt(normalizedEmail);
            log.warn("Authentication failed: invalid password [email={}]", maskEmail(normalizedEmail));
            throw new InvalidCredentialsException();
        }

        // 4. Check user status (suspended accounts receive 403 ACCESS_DENIED)
        if (user.isSuspended()) {
            log.warn("Authentication rejected: account suspended [userId={}]", user.getId());
            throw new UserSuspendedException();
        }

        // 5. Successful login: reset rate limit counters
        rateLimiterService.resetAttempts(normalizedEmail);
        log.info("User authenticated successfully [userId={}]", user.getId());

        return user;
    }

    @Override
    public LoginResult login(LoginRequest request) {
        User user = authenticate(request);
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

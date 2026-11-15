package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RefreshTokenRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtService;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenGenerator;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import lk.ac.mrt.swiftdrop.identity_service.service.TokenRotationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Production implementation of RefreshTokenService.
 * Enforces OAuth 2.0 BCP token rotation and automatic replay attack mitigation (§4.3).
 */
@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtService jwtService;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenGenerator refreshTokenGenerator,
            JwtService jwtService
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository, "refreshTokenRepository must not be null");
        this.refreshTokenGenerator = Objects.requireNonNull(refreshTokenGenerator, "refreshTokenGenerator must not be null");
        this.jwtService = Objects.requireNonNull(jwtService, "jwtService must not be null");
    }

    @Override
    public String issueRefreshToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("User and user ID must not be null when issuing refresh token");
        }

        String rawToken = refreshTokenGenerator.generateToken();
        RefreshToken refreshToken = refreshTokenGenerator.createRefreshToken(user, rawToken);
        refreshTokenRepository.save(refreshToken);

        log.debug("Issued initial refresh token for user [userId={}, tokenId={}]", user.getId(), refreshToken.getId());
        return rawToken;
    }

    @Override
    public TokenRotationResult rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token is required");
        }

        String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken.trim());
        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh failed: token hash not recognized");
                    return new InvalidTokenException("Invalid refresh token");
                });

        // 1. Replay attack detection: if token is already revoked, invalidate entire chain
        if (token.isRevoked()) {
            UUID userId = token.getUser().getId();
            refreshTokenRepository.revokeAllForUserId(userId, Instant.now());
            log.warn("SECURITY ALERT: Replay attack detected for user [userId={}]. Invaliding all refresh tokens in family.", userId);
            throw new InvalidTokenException("Refresh token has already been used. Session invalidated for security.");
        }

        // 2. Expiration check
        if (token.isExpired()) {
            log.warn("Refresh failed: token expired for user [userId={}, expiresAt={}]", token.getUser().getId(), token.getExpiresAt());
            throw new InvalidTokenException("Refresh token has expired");
        }

        // 3. User status check
        User user = token.getUser();
        if (user.isSuspended()) {
            log.warn("Refresh rejected: user account is suspended [userId={}]", user.getId());
            throw new UserSuspendedException();
        }

        // 4. Atomically issue new refresh token and rotate old token
        String newRawToken = refreshTokenGenerator.generateToken();
        RefreshToken newRefreshToken = refreshTokenGenerator.createRefreshToken(user, newRawToken);
        newRefreshToken = refreshTokenRepository.save(newRefreshToken);

        token.rotate(newRefreshToken.getId());
        refreshTokenRepository.save(token);

        // 5. Issue new RS256 JWT access token
        String newAccessToken = jwtService.generateAccessToken(user);
        long expiresInSeconds = jwtService.getAccessTokenTtl().toSeconds();

        log.info("Successfully rotated refresh token for user [userId={}, oldTokenId={}, newTokenId={}]",
                user.getId(), token.getId(), newRefreshToken.getId());

        return new TokenRotationResult(newAccessToken, newRawToken, expiresInSeconds, user);
    }

    @Override
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        try {
            String tokenHash = refreshTokenGenerator.hashToken(rawRefreshToken.trim());
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                if (!token.isRevoked()) {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("Revoked refresh token [tokenId={}, userId={}]", token.getId(), token.getUser().getId());
                }
            });
        } catch (Exception e) {
            log.warn("Error while revoking refresh token", e);
        }
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        if (userId == null) {
            return;
        }
        int revokedCount = refreshTokenRepository.revokeAllForUserId(userId, Instant.now());
        log.info("Revoked all active refresh tokens for user [userId={}, count={}]", userId, revokedCount);
    }
}

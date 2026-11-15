package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RefreshTokenRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RefreshTokenGenerator refreshTokenGenerator;

    @Mock
    private JwtService jwtService;

    private RefreshTokenService refreshTokenService;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenServiceImpl(
                refreshTokenRepository,
                refreshTokenGenerator,
                jwtService
        );

        userId = UUID.randomUUID();
        testUser = new User("amandi.arangala@swiftdrop.lk", "$2b$10$hash", "Amandi Arangala", "+94771234567");
        testUser.setId(userId);
        testUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer role"));
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should issue initial refresh token and persist hashed record")
    void shouldIssueRefreshTokenSuccessfully() {
        String rawToken = "raw-refresh-token-value-1234567890";
        RefreshToken entity = new RefreshToken(testUser, "hash123", Instant.now().plus(7, ChronoUnit.DAYS));

        when(refreshTokenGenerator.generateToken()).thenReturn(rawToken);
        when(refreshTokenGenerator.createRefreshToken(testUser, rawToken)).thenReturn(entity);

        String result = refreshTokenService.issueRefreshToken(testUser);

        assertThat(result).isEqualTo(rawToken);
        verify(refreshTokenRepository).save(entity);
    }

    @Test
    @DisplayName("Should reject issuing refresh token when user or user ID is null")
    void shouldRejectIssueWhenUserNull() {
        assertThatThrownBy(() -> refreshTokenService.issueRefreshToken(null))
                .isInstanceOf(IllegalArgumentException.class);

        User unsavedUser = new User("test@swiftdrop.lk", "hash", "Name", "+94770000000");
        assertThatThrownBy(() -> refreshTokenService.issueRefreshToken(unsavedUser))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should rotate valid refresh token: issue new pair, mark old as replaced, and generate access token")
    void shouldRotateValidTokenSuccessfully() {
        String oldRawToken = "old-raw-token";
        String oldHash = "old-hash-sha256";
        Instant futureExpiry = Instant.now().plus(5, ChronoUnit.DAYS);

        RefreshToken oldTokenEntity = new RefreshToken(testUser, oldHash, futureExpiry);
        oldTokenEntity.setId(UUID.randomUUID());

        String newRawToken = "new-raw-token";
        UUID newTokenId = UUID.randomUUID();
        RefreshToken newTokenEntity = new RefreshToken(testUser, "new-hash", Instant.now().plus(7, ChronoUnit.DAYS));
        newTokenEntity.setId(newTokenId);

        when(refreshTokenGenerator.hashToken(oldRawToken)).thenReturn(oldHash);
        when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(oldTokenEntity));
        when(refreshTokenGenerator.generateToken()).thenReturn(newRawToken);
        when(refreshTokenGenerator.createRefreshToken(testUser, newRawToken)).thenReturn(newTokenEntity);
        when(refreshTokenRepository.save(newTokenEntity)).thenReturn(newTokenEntity);
        when(jwtService.generateAccessToken(testUser)).thenReturn("new-access-token-jwt");
        when(jwtService.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));

        TokenRotationResult result = refreshTokenService.rotate(oldRawToken);

        assertThat(result).isNotNull();
        assertThat(result.accessToken()).isEqualTo("new-access-token-jwt");
        assertThat(result.rawRefreshToken()).isEqualTo(newRawToken);
        assertThat(result.expiresIn()).isEqualTo(900L);
        assertThat(result.user()).isEqualTo(testUser);

        // Verify old token was rotated
        assertThat(oldTokenEntity.isRevoked()).isTrue();
        assertThat(oldTokenEntity.getReplacedBy()).isEqualTo(newTokenId);
        verify(refreshTokenRepository).save(oldTokenEntity);

        // Verify DTO conversion
        AuthResponse authResponse = result.toAuthResponse();
        assertThat(authResponse.accessToken()).isEqualTo("new-access-token-jwt");
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.expiresIn()).isEqualTo(900L);
        assertThat(authResponse.user().email()).isEqualTo("amandi.arangala@swiftdrop.lk");
    }

    @Test
    @DisplayName("Security Defense: Replay attack on revoked token must revoke entire token family for user")
    void shouldDetectReplayAttackAndRevokeAllTokensForUser() {
        String replayedRawToken = "replayed-raw-token";
        String replayedHash = "replayed-hash-sha256";

        RefreshToken revokedTokenEntity = new RefreshToken(testUser, replayedHash, Instant.now().plus(3, ChronoUnit.DAYS));
        revokedTokenEntity.setId(UUID.randomUUID());
        revokedTokenEntity.rotate(UUID.randomUUID()); // Already revoked and replaced!

        when(refreshTokenGenerator.hashToken(replayedRawToken)).thenReturn(replayedHash);
        when(refreshTokenRepository.findByTokenHash(replayedHash)).thenReturn(Optional.of(revokedTokenEntity));

        assertThatThrownBy(() -> refreshTokenService.rotate(replayedRawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Refresh token has already been used");

        // Verify ALL active tokens for this user were immediately invalidated
        verify(refreshTokenRepository).revokeAllForUserId(eq(userId), any(Instant.class));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("Should reject expired refresh token without revoking token family")
    void shouldRejectExpiredToken() {
        String expiredRawToken = "expired-raw-token";
        String expiredHash = "expired-hash";

        RefreshToken expiredTokenEntity = new RefreshToken(testUser, expiredHash, Instant.now().minus(1, ChronoUnit.HOURS));
        expiredTokenEntity.setId(UUID.randomUUID());

        when(refreshTokenGenerator.hashToken(expiredRawToken)).thenReturn(expiredHash);
        when(refreshTokenRepository.findByTokenHash(expiredHash)).thenReturn(Optional.of(expiredTokenEntity));

        assertThatThrownBy(() -> refreshTokenService.rotate(expiredRawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Refresh token has expired");

        // Should NOT trigger family revocation for innocent expiration
        verify(refreshTokenRepository, never()).revokeAllForUserId(any(), any());
    }

    @Test
    @DisplayName("Should reject non-existent refresh token")
    void shouldRejectNonExistentToken() {
        String unknownToken = "unknown-token";
        String hash = "unknown-hash";

        when(refreshTokenGenerator.hashToken(unknownToken)).thenReturn(hash);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.rotate(unknownToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid refresh token");
    }

    @Test
    @DisplayName("Should reject refresh when user account is suspended")
    void shouldRejectSuspendedUser() {
        testUser.suspend();
        String rawToken = "raw-token";
        String hash = "hash";

        RefreshToken tokenEntity = new RefreshToken(testUser, hash, Instant.now().plus(5, ChronoUnit.DAYS));
        tokenEntity.setId(UUID.randomUUID());

        when(refreshTokenGenerator.hashToken(rawToken)).thenReturn(hash);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(tokenEntity));

        assertThatThrownBy(() -> refreshTokenService.rotate(rawToken))
                .isInstanceOf(UserSuspendedException.class);
    }

    @Test
    @DisplayName("Should reject null or blank raw token on rotate")
    void shouldRejectBlankTokenOnRotate() {
        assertThatThrownBy(() -> refreshTokenService.rotate(null))
                .isInstanceOf(InvalidTokenException.class);

        assertThatThrownBy(() -> refreshTokenService.rotate("   "))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Should revoke specific token by raw token value")
    void shouldRevokeTokenSuccessfully() {
        String rawToken = "raw-token-to-revoke";
        String hash = "hash-to-revoke";

        RefreshToken tokenEntity = new RefreshToken(testUser, hash, Instant.now().plus(5, ChronoUnit.DAYS));
        tokenEntity.setId(UUID.randomUUID());

        when(refreshTokenGenerator.hashToken(rawToken)).thenReturn(hash);
        when(refreshTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(tokenEntity));

        refreshTokenService.revokeToken(rawToken);

        assertThat(tokenEntity.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(tokenEntity);
    }

    @Test
    @DisplayName("Should revoke all tokens for user ID")
    void shouldRevokeAllTokensForUser() {
        when(refreshTokenRepository.revokeAllForUserId(eq(userId), any(Instant.class))).thenReturn(3);

        refreshTokenService.revokeAllForUser(userId);

        verify(refreshTokenRepository).revokeAllForUserId(eq(userId), any(Instant.class));
    }
}

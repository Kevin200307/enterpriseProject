package lk.ac.mrt.swiftdrop.identity_service.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    private User testUser;
    private String testHash;

    @BeforeEach
    void setUp() {
        testUser = new User("amandi.arangala@swiftdrop.lk", "hashedPass", "Amandi Arangala", "+94771234567");
        testUser.setId(UUID.randomUUID());
        testHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    }

    @Test
    @DisplayName("Should initialize active refresh token with valid attributes")
    void shouldInitializeActiveRefreshToken() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        RefreshToken token = new RefreshToken(testUser, testHash, expiresAt);

        assertThat(token.getUser()).isEqualTo(testUser);
        assertThat(token.getTokenHash()).isEqualTo(testHash);
        assertThat(token.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(token.getRevokedAt()).isNull();
        assertThat(token.getReplacedBy()).isNull();
        assertThat(token.isExpired()).isFalse();
        assertThat(token.isRevoked()).isFalse();
        assertThat(token.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should report isExpired as true when expiresAt is in the past")
    void shouldDetectExpiredToken() {
        Instant pastExpiresAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        RefreshToken token = new RefreshToken(testUser, testHash, pastExpiresAt);

        assertThat(token.isExpired()).isTrue();
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should revoke token and update revokedAt timestamp")
    void shouldRevokeToken() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        RefreshToken token = new RefreshToken(testUser, testHash, expiresAt);

        token.revoke();

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should rotate token and record replacement token ID for replay detection")
    void shouldRotateTokenWithReplacementId() {
        Instant expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        RefreshToken token = new RefreshToken(testUser, testHash, expiresAt);
        UUID newRefreshTokenId = UUID.randomUUID();

        token.rotate(newRefreshTokenId);

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokedAt()).isNotNull();
        assertThat(token.getReplacedBy()).isEqualTo(newRefreshTokenId);
        assertThat(token.isActive()).isFalse();
    }

    @Test
    @DisplayName("Should mask tokenHash in toString output for security")
    void shouldMaskTokenHashInToString() {
        RefreshToken token = new RefreshToken(testUser, testHash, Instant.now().plus(7, ChronoUnit.DAYS));

        String toString = token.toString();
        assertThat(toString).contains("e3b0c442...");
        assertThat(toString).doesNotContain(testHash); // Must not leak the entire hash
    }

    @Test
    @DisplayName("Should satisfy equals and hashCode contract by id or tokenHash")
    void shouldSatisfyEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        RefreshToken token1 = new RefreshToken(testUser, testHash, Instant.now().plus(7, ChronoUnit.DAYS));
        token1.setId(id1);

        RefreshToken token2 = new RefreshToken(testUser, testHash, Instant.now().plus(7, ChronoUnit.DAYS));
        token2.setId(id1);

        RefreshToken token3 = new RefreshToken(testUser, "otherhash", Instant.now().plus(7, ChronoUnit.DAYS));
        token3.setId(UUID.randomUUID());

        assertThat(token1).isEqualTo(token2);
        assertThat(token1.hashCode()).isEqualTo(token2.hashCode());
        assertThat(token1).isNotEqualTo(token3);
    }
}

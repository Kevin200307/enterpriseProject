package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.RefreshTokenGeneratorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenGeneratorTest {

    private RefreshTokenGenerator generator;
    private JwtProperties jwtProperties;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800, // 7 days (604800 seconds)
                "",
                "",
                "swiftdrop-v1"
        );
        generator = new RefreshTokenGeneratorImpl(jwtProperties);

        testUser = new User("amandi.arangala@swiftdrop.lk", "hash", "Amandi Arangala", "+94771234567");
        testUser.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should generate cryptographically strong URL-safe 256-bit token")
    void shouldGenerateUrlSafeToken() {
        String token = generator.generateToken();

        assertThat(token).isNotBlank();
        // 32 bytes in Base64URL without padding is exactly 43 characters
        assertThat(token).hasSize(43);
        // Must contain only Base64URL characters (A-Z, a-z, 0-9, -, _)
        assertThat(token).matches("^[A-Za-z0-9_-]{43}$");
    }

    @Test
    @DisplayName("Should generate unique tokens with high entropy across 1000 generations")
    void shouldGenerateUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = generator.generateToken();
            boolean added = tokens.add(token);
            assertThat(added).isTrue(); // No collisions
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    @DisplayName("Should compute 64-character lowercase SHA-256 hexadecimal hash")
    void shouldHashTokenWithSha256() {
        String token = generator.generateToken();
        String hash = generator.hashToken(token);

        assertThat(hash).isNotBlank();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Should hash deterministically for identical tokens and differently for distinct tokens")
    void shouldHashDeterministically() {
        String tokenA = generator.generateToken();
        String tokenB = generator.generateToken();

        String hashA1 = generator.hashToken(tokenA);
        String hashA2 = generator.hashToken(tokenA);
        String hashB = generator.hashToken(tokenB);

        assertThat(hashA1).isEqualTo(hashA2);
        assertThat(hashA1).isNotEqualTo(hashB);
    }

    @Test
    @DisplayName("Should reject null or blank raw token when hashing")
    void shouldRejectInvalidTokenWhenHashing() {
        assertThatThrownBy(() -> generator.hashToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raw refresh token must not be null or blank");

        assertThatThrownBy(() -> generator.hashToken("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Raw refresh token must not be null or blank");
    }

    @Test
    @DisplayName("Should create RefreshToken entity with configured 7-day TTL")
    void shouldCreateRefreshTokenWithConfiguredTtl() {
        String rawToken = generator.generateToken();
        Instant before = Instant.now().plusSeconds(604800);

        RefreshToken refreshToken = generator.createRefreshToken(testUser, rawToken);
        Instant after = Instant.now().plusSeconds(604800);

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.getUser()).isEqualTo(testUser);
        assertThat(refreshToken.getTokenHash()).isEqualTo(generator.hashToken(rawToken));
        assertThat(refreshToken.getExpiresAt()).isBetween(before.minusSeconds(2), after.plusSeconds(2));
        assertThat(refreshToken.isActive()).isTrue();
    }

    @Test
    @DisplayName("Should create RefreshToken entity with custom TTL")
    void shouldCreateRefreshTokenWithCustomTtl() {
        String rawToken = generator.generateToken();
        Duration customTtl = Duration.ofHours(24);
        Instant before = Instant.now().plus(customTtl);

        RefreshToken refreshToken = generator.createRefreshToken(testUser, rawToken, customTtl);
        Instant after = Instant.now().plus(customTtl);

        assertThat(refreshToken).isNotNull();
        assertThat(refreshToken.getExpiresAt()).isBetween(before.minusSeconds(2), after.plusSeconds(2));
    }
}

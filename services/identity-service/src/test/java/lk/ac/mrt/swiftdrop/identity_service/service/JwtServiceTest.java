package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProviderImpl;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityBeansConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.JwtServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;
    private JwtProperties jwtProperties;
    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "http://identity-service:8080",
                900, // 15 minutes
                604800,
                "",
                "",
                "swiftdrop-identity-key-1"
        );
        RsaKeyProvider rsaKeyProvider = new RsaKeyProviderImpl(jwtProperties, new MockEnvironment());
        SecurityBeansConfig securityBeansConfig = new SecurityBeansConfig();

        JwtEncoder jwtEncoder = securityBeansConfig.jwtEncoder(rsaKeyProvider);
        JwtDecoder jwtDecoder = securityBeansConfig.jwtDecoder(rsaKeyProvider);

        jwtService = new JwtServiceImpl(jwtEncoder, jwtDecoder, rsaKeyProvider, jwtProperties);

        userId = UUID.randomUUID();
        testUser = new User("amandi.arangala@swiftdrop.lk", "$2b$10$hash", "Amandi Arangala", "+94771234567");
        testUser.setId(userId);
        testUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer role"));
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should generate valid RS256 signed access token with exact contract claims (§4.3)")
    void shouldGenerateValidAccessTokenWithStandardClaims() {
        String token = jwtService.generateAccessToken(testUser);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3); // Header, payload, signature

        // Decode and verify claims
        Jwt jwt = jwtService.decodeToken(token);

        // Header claims
        assertThat(jwt.getHeaders().get("alg")).isEqualTo("RS256");
        assertThat(jwt.getHeaders().get("kid")).isEqualTo("swiftdrop-identity-key-1");

        // Payload claims
        assertThat(jwt.getIssuer().toString()).isEqualTo("http://identity-service:8080");
        assertThat(jwt.getAudience()).containsExactly("swiftdrop");
        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("amandi.arangala@swiftdrop.lk");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(jwt.getClaimAsString("merchantId")).isNull();

        // 15-minute expiration window
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        Duration ttl = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt());
        assertThat(ttl.toSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("Should include merchantId claim for MERCHANT role users")
    void shouldIncludeMerchantIdClaimWhenProvided() {
        UUID merchantId = UUID.randomUUID();
        testUser.addRole(new RoleEntity(Role.MERCHANT, "Merchant role"));

        String token = jwtService.generateAccessToken(testUser, merchantId);

        Jwt jwt = jwtService.decodeToken(token);
        assertThat(jwt.getClaimAsString("merchantId")).isEqualTo(merchantId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER", "MERCHANT");
    }

    @Test
    @DisplayName("Should include multiple roles alphabetically sorted in JWT claims")
    void shouldIncludeMultipleRolesSorted() {
        testUser.addRole(new RoleEntity(Role.ADMIN, "Admin role"));

        String token = jwtService.generateAccessToken(testUser);

        Jwt jwt = jwtService.decodeToken(token);
        List<String> roles = jwt.getClaimAsStringList("roles");
        assertThat(roles).containsExactly("ADMIN", "CUSTOMER");
    }

    @Test
    @DisplayName("Should reject token generation when user or user ID is null")
    void shouldRejectWhenUserOrUserIdIsNull() {
        assertThatThrownBy(() -> jwtService.generateAccessToken(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User and user ID must not be null");

        User userWithoutId = new User("test@swiftdrop.lk", "hash", "Name", "+94771234567");
        assertThatThrownBy(() -> jwtService.generateAccessToken(userWithoutId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User and user ID must not be null");
    }

    @Test
    @DisplayName("Should reject and throw JwtException when token signature is tampered")
    void shouldRejectTamperedToken() {
        String token = jwtService.generateAccessToken(testUser);
        String[] parts = token.split("\\.");

        // Tamper with signature
        String tamperedSignature = parts[2].substring(0, parts[2].length() - 4) + "XXXX";
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> jwtService.decodeToken(tamperedToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("Should return 15-minute access token TTL duration")
    void shouldReturnCorrectTtlDuration() {
        assertThat(jwtService.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
    }
}

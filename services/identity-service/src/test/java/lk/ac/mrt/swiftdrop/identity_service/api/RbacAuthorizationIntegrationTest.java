package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.BatchUserLookupRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateUserRolesRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateUserStatusRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserLookupResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProviderImpl;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityBeansConfig;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprehensive RBAC and Authorization Test Suite.
 * Serves as the authoritative template that downstream resource server teams copy.
 *
 * Covers:
 * 1. Unauthenticated requests get 401 UNAUTHORIZED (with RFC 7807 problem details)
 * 2. Expired RS256 access tokens get 401 UNAUTHORIZED
 * 3. Malformed or rogue-key tokens get 401 UNAUTHORIZED
 * 4. CUSTOMER role cannot call any /api/v1/users admin routes (403 FORBIDDEN)
 * 5. Other non-admin roles (COURIER, MERCHANT, DISPATCHER) get 403 FORBIDDEN on admin routes
 * 6. Admin users with valid ADMIN role succeed (200 OK)
 * 7. Non-admin users are authorized for their own profile (/api/v1/users/me)
 */
@WebMvcTest(controllers = {UserController.class, InternalUserLookupController.class})
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        SecurityBeansConfig.class,
        RsaKeyProviderImpl.class,
        RbacAuthorizationIntegrationTest.TestPropertiesConfig.class
})
class RbacAuthorizationIntegrationTest {

    @TestConfiguration
    static class TestPropertiesConfig {
        @Bean
        public JwtProperties jwtProperties() {
            return new JwtProperties(
                    "http://identity-service:8080",
                    "swiftdrop",
                    900L,
                    604800L,
                    "",
                    "",
                    "swiftdrop-v1"
            );
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private RsaKeyProvider rsaKeyProvider;

    @MockitoBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private static final UUID TARGET_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUpMocks() {
        UserResponse mockResponse = new UserResponse(
                TARGET_USER_ID,
                "target@swiftdrop.lk",
                "Target User",
                "+94770000000",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );

        when(userService.listUsers(any(), any(), any())).thenReturn(new PageImpl<>(List.of(mockResponse)));
        when(userService.getUserProfile(any())).thenReturn(mockResponse);
        when(userService.updateUserStatus(any(), any(), any())).thenReturn(mockResponse);
        when(userService.updateUserRoles(any(), any(), any())).thenReturn(mockResponse);
        when(userService.lookupUsers(any())).thenReturn(List.of());
    }

    // =========================================================================
    // 1. Unauthenticated Requests (401 UNAUTHORIZED)
    // =========================================================================

    @Nested
    @DisplayName("1. Unauthenticated Requests (401 UNAUTHORIZED)")
    class UnauthenticatedRequests {

        @Test
        @DisplayName("GET /api/v1/users without token returns 401")
        void shouldReturn401WhenGetUsersUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
        }

        @Test
        @DisplayName("PATCH /api/v1/users/{id}/status without token returns 401")
        void shouldReturn401WhenPatchStatusUnauthenticated() throws Exception {
            UpdateUserStatusRequest body = new UpdateUserStatusRequest(UserStatus.SUSPENDED);
            mockMvc.perform(patch("/api/v1/users/{id}/status", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
        }

        @Test
        @DisplayName("PATCH /api/v1/users/{id}/roles without token returns 401")
        void shouldReturn401WhenPatchRolesUnauthenticated() throws Exception {
            UpdateUserRolesRequest body = new UpdateUserRolesRequest(Set.of(Role.ADMIN));
            mockMvc.perform(patch("/api/v1/users/{id}/roles", TARGET_USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
        }

        @Test
        @DisplayName("GET /api/v1/users/me without token returns 401")
        void shouldReturn401WhenGetMeUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
        }

        @Test
        @DisplayName("POST /api/v1/internal/users/lookup without token returns 401")
        void shouldReturn401WhenInternalLookupUnauthenticated() throws Exception {
            BatchUserLookupRequest body = new BatchUserLookupRequest(List.of(TARGET_USER_ID));
            mockMvc.perform(post("/api/v1/internal/users/lookup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
        }
    }

    // =========================================================================
    // 2. Expired Token Requests (401 UNAUTHORIZED)
    // =========================================================================

    @Nested
    @DisplayName("2. Expired Token Requests (401 UNAUTHORIZED)")
    class ExpiredTokenRequests {

        @Test
        @DisplayName("GET /api/v1/users with expired token returns 401")
        void shouldReturn401WhenTokenIsExpiredForAdminRoute() throws Exception {
            String expiredToken = generateExpiredToken(UUID.randomUUID(), "admin@swiftdrop.lk", List.of(Role.ADMIN));

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/users/me with expired token returns 401")
        void shouldReturn401WhenTokenIsExpiredForMeRoute() throws Exception {
            String expiredToken = generateExpiredToken(UUID.randomUUID(), "customer@swiftdrop.lk", List.of(Role.CUSTOMER));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("PATCH /api/v1/users/{id}/status with expired token returns 401")
        void shouldReturn401WhenTokenIsExpiredForPatchStatus() throws Exception {
            String expiredToken = generateExpiredToken(UUID.randomUUID(), "admin@swiftdrop.lk", List.of(Role.ADMIN));
            UpdateUserStatusRequest body = new UpdateUserStatusRequest(UserStatus.SUSPENDED);

            mockMvc.perform(patch("/api/v1/users/{id}/status", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 3. Malformed and Rogue Key Tokens (401 UNAUTHORIZED)
    // =========================================================================

    @Nested
    @DisplayName("3. Malformed and Rogue Key Tokens (401 UNAUTHORIZED)")
    class InvalidTokenRequests {

        @Test
        @DisplayName("GET /api/v1/users with malformed bearer token returns 401")
        void shouldReturn401WhenTokenIsMalformed() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-malformed-token-string"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("GET /api/v1/users with token signed by untrusted RSA key returns 401")
        void shouldReturn401WhenTokenSignedByRogueKey() throws Exception {
            String rogueToken = generateTokenWithRogueKey(UUID.randomUUID(), "admin@swiftdrop.lk", List.of(Role.ADMIN));

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + rogueToken))
                    .andExpect(status().isUnauthorized());
        }
    }

    // =========================================================================
    // 4. CUSTOMER Role Calling Admin Routes (403 FORBIDDEN)
    // =========================================================================

    @Nested
    @DisplayName("4. CUSTOMER Role Calling Admin Routes (403 FORBIDDEN)")
    class CustomerForbiddenRequests {

        private String customerToken;

        @BeforeEach
        void setUp() {
            customerToken = generateToken(UUID.randomUUID(), "customer@swiftdrop.lk", List.of(Role.CUSTOMER), Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("CUSTOMER cannot call GET /api/v1/users (returns 403)")
        void shouldReturn403WhenCustomerCallsGetUsers() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }

        @Test
        @DisplayName("CUSTOMER cannot call PATCH /api/v1/users/{id}/status (returns 403)")
        void shouldReturn403WhenCustomerCallsPatchStatus() throws Exception {
            UpdateUserStatusRequest body = new UpdateUserStatusRequest(UserStatus.SUSPENDED);

            mockMvc.perform(patch("/api/v1/users/{id}/status", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }

        @Test
        @DisplayName("CUSTOMER cannot call PATCH /api/v1/users/{id}/roles (returns 403)")
        void shouldReturn403WhenCustomerCallsPatchRoles() throws Exception {
            UpdateUserRolesRequest body = new UpdateUserRolesRequest(Set.of(Role.ADMIN));

            mockMvc.perform(patch("/api/v1/users/{id}/roles", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }

        @Test
        @DisplayName("CUSTOMER cannot call POST /api/v1/internal/users/lookup (returns 403)")
        void shouldReturn403WhenCustomerCallsInternalLookup() throws Exception {
            BatchUserLookupRequest body = new BatchUserLookupRequest(List.of(TARGET_USER_ID));

            mockMvc.perform(post("/api/v1/internal/users/lookup")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }
    }

    // =========================================================================
    // 5. Valid Tokens with Wrong Role (403 FORBIDDEN)
    // =========================================================================

    @Nested
    @DisplayName("5. Valid Tokens with Wrong Role (403 FORBIDDEN)")
    class WrongRoleRequests {

        @Test
        @DisplayName("COURIER role cannot call GET /api/v1/users (returns 403)")
        void shouldReturn403WhenCourierCallsGetUsers() throws Exception {
            String courierToken = generateToken(UUID.randomUUID(), "courier@swiftdrop.lk", List.of(Role.COURIER), Duration.ofMinutes(15));

            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + courierToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }

        @Test
        @DisplayName("MERCHANT role cannot call PATCH /api/v1/users/{id}/status (returns 403)")
        void shouldReturn403WhenMerchantCallsPatchStatus() throws Exception {
            String merchantToken = generateToken(UUID.randomUUID(), "merchant@swiftdrop.lk", List.of(Role.MERCHANT), Duration.ofMinutes(15));
            UpdateUserStatusRequest body = new UpdateUserStatusRequest(UserStatus.SUSPENDED);

            mockMvc.perform(patch("/api/v1/users/{id}/status", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }

        @Test
        @DisplayName("DISPATCHER role cannot call PATCH /api/v1/users/{id}/roles (returns 403)")
        void shouldReturn403WhenDispatcherCallsPatchRoles() throws Exception {
            String dispatcherToken = generateToken(UUID.randomUUID(), "dispatcher@swiftdrop.lk", List.of(Role.DISPATCHER), Duration.ofMinutes(15));
            UpdateUserRolesRequest body = new UpdateUserRolesRequest(Set.of(Role.ADMIN));

            mockMvc.perform(patch("/api/v1/users/{id}/roles", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + dispatcherToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
        }
    }

    // =========================================================================
    // 6. Authorized Requests (200 OK)
    // =========================================================================

    @Nested
    @DisplayName("6. Authorized Requests (200 OK)")
    class AuthorizedRequests {

        private String adminToken;

        @BeforeEach
        void setUp() {
            adminToken = generateToken(UUID.randomUUID(), "admin@swiftdrop.lk", List.of(Role.ADMIN), Duration.ofMinutes(15));
        }

        @Test
        @DisplayName("ADMIN role can call GET /api/v1/users (returns 200)")
        void shouldReturn200WhenAdminCallsGetUsers() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("ADMIN role can call PATCH /api/v1/users/{id}/status (returns 200)")
        void shouldReturn200WhenAdminCallsPatchStatus() throws Exception {
            UpdateUserStatusRequest body = new UpdateUserStatusRequest(UserStatus.SUSPENDED);

            mockMvc.perform(patch("/api/v1/users/{id}/status", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TARGET_USER_ID.toString()));
        }

        @Test
        @DisplayName("ADMIN role can call PATCH /api/v1/users/{id}/roles (returns 200)")
        void shouldReturn200WhenAdminCallsPatchRoles() throws Exception {
            UpdateUserRolesRequest body = new UpdateUserRolesRequest(Set.of(Role.ADMIN, Role.DISPATCHER));

            mockMvc.perform(patch("/api/v1/users/{id}/roles", TARGET_USER_ID)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(TARGET_USER_ID.toString()));
        }

        @Test
        @DisplayName("ADMIN role can call POST /api/v1/internal/users/lookup (returns 200)")
        void shouldReturn200WhenAdminCallsInternalLookup() throws Exception {
            BatchUserLookupRequest body = new BatchUserLookupRequest(List.of(TARGET_USER_ID));

            mockMvc.perform(post("/api/v1/internal/users/lookup")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("CUSTOMER role can call GET /api/v1/users/me for their own profile (returns 200)")
        void shouldReturn200WhenCustomerCallsGetMe() throws Exception {
            UUID customerId = UUID.randomUUID();
            String customerToken = generateToken(customerId, "customer@swiftdrop.lk", List.of(Role.CUSTOMER), Duration.ofMinutes(15));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("COURIER role can call GET /api/v1/users/me for their own profile (returns 200)")
        void shouldReturn200WhenCourierCallsGetMe() throws Exception {
            UUID courierId = UUID.randomUUID();
            String courierToken = generateToken(courierId, "courier@swiftdrop.lk", List.of(Role.COURIER), Duration.ofMinutes(15));

            mockMvc.perform(get("/api/v1/users/me")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + courierToken))
                    .andExpect(status().isOk());
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String generateToken(UUID userId, String email, List<Role> roles, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);

        List<String> roleNames = roles.stream().map(Role::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://identity-service:8080")
                .audience(List.of("swiftdrop"))
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKeyProvider.getKeyId())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String generateExpiredToken(UUID userId, String email, List<Role> roles) {
        Instant now = Instant.now().minus(Duration.ofMinutes(30));
        Instant expiresAt = now.plus(Duration.ofMinutes(15)); // Expired 15 minutes ago

        List<String> roleNames = roles.stream().map(Role::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://identity-service:8080")
                .audience(List.of("swiftdrop"))
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roleNames)
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(rsaKeyProvider.getKeyId())
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String generateTokenWithRogueKey(UUID userId, String email, List<Role> roles) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair roguePair = kpg.generateKeyPair();

        RSAKey rogueJwk = new RSAKey.Builder((RSAPublicKey) roguePair.getPublic())
                .privateKey((RSAPrivateKey) roguePair.getPrivate())
                .keyID("rogue-key-id")
                .build();

        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rogueJwk));
        NimbusJwtEncoder rogueEncoder = new NimbusJwtEncoder(jwkSource);

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://identity-service:8080")
                .audience(List.of("swiftdrop"))
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles.stream().map(Role::name).toList())
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(15)))
                .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId("rogue-key-id")
                .build();

        return rogueEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}

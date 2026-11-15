package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProviderImpl;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityBeansConfig;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.repository.RefreshTokenRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.RateLimiterService;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenGenerator;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.AuthServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.JwtServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.RefreshTokenGeneratorImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.RefreshTokenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration slice tests for IDN-2 covering:
 * 1. Public RFC 7517 JWKS publication
 * 2. RS256 JWT access token generation and claims validation (§4.3)
 * 3. Independent token verification simulating downstream resource services
 * 4. 15-minute access token TTL verification
 * 5. HttpOnly, Secure, SameSite=Strict refresh cookie handling
 * 6. Refresh token atomic rotation with replaced_by audit linkage
 * 7. Replay attack detection and complete token family revocation
 * 8. User logout and cookie clearance
 */
@WebMvcTest(controllers = {AuthController.class, JwksController.class})
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        SecurityBeansConfig.class,
        RsaKeyProviderImpl.class,
        JwtServiceImpl.class,
        RefreshTokenGeneratorImpl.class,
        RefreshTokenServiceImpl.class,
        AuthCookieService.class,
        AuthServiceImpl.class,
        AuthTokenFlowIntegrationTest.TestPropertiesConfig.class
})
class AuthTokenFlowIntegrationTest {

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
                    "swiftdrop-identity-key-1"
            );
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RsaKeyProvider rsaKeyProvider;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    private User testUser;
    private UUID userId;
    private final String rawPassword = "ValidPassword123!";

    // In-memory simulation of the refresh_tokens table
    private final Map<String, RefreshToken> tokenHashStorage = new ConcurrentHashMap<>();
    private final Map<UUID, List<RefreshToken>> userTokensStorage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        tokenHashStorage.clear();
        userTokensStorage.clear();

        userId = UUID.randomUUID();
        testUser = new User("amandi.arangala@swiftdrop.lk", passwordEncoder.encode(rawPassword), "Amandi Arangala", "+94771234567");
        testUser.setId(userId);
        testUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer role"));
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());

        // Default mock behaviors
        when(userRepository.findByEmailIgnoreCase(testUser.getEmail())).thenReturn(Optional.of(testUser));

        // State-preserving in-memory mock for RefreshTokenRepository
        when(refreshTokenRepository.findByTokenHash(anyString())).thenAnswer(invocation -> {
            String hash = invocation.getArgument(0);
            return Optional.ofNullable(tokenHashStorage.get(hash));
        });

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            tokenHashStorage.put(token.getTokenHash(), token);
            userTokensStorage.computeIfAbsent(token.getUser().getId(), k -> new ArrayList<>()).add(token);
            return token;
        });

        when(refreshTokenRepository.revokeAllForUserId(any(UUID.class), any(Instant.class))).thenAnswer(invocation -> {
            UUID uid = invocation.getArgument(0);
            Instant revokedAt = invocation.getArgument(1);
            List<RefreshToken> list = userTokensStorage.getOrDefault(uid, List.of());
            int count = 0;
            for (RefreshToken t : list) {
                if (!t.isRevoked()) {
                    t.revoke();
                    count++;
                }
            }
            return count;
        });
    }

    @Test
    @DisplayName("JWKS: Public endpoint exposes valid RFC 7517 key matching the signing key")
    void shouldPublishValidJwksMatchingSigningKey() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].kid").value(rsaKeyProvider.getKeyId()))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"));
    }

    @Test
    @DisplayName("Login & JWT: Issues valid RS256 access token with §4.3 claims and HttpOnly refresh cookie")
    void shouldLoginAndIssueValidRs256AccessTokenAndRefreshCookie() throws Exception {
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), rawPassword);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andReturn();

        // 1. Verify JSON body
        String responseContent = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseContent, AuthResponse.class);

        assertThat(authResponse.accessToken()).isNotBlank();
        assertThat(authResponse.tokenType()).isEqualTo("Bearer");
        assertThat(authResponse.expiresIn()).isEqualTo(900L);
        assertThat(authResponse.user().email()).isEqualTo(testUser.getEmail());
        assertThat(authResponse.user().fullName()).isEqualTo(testUser.getFullName());
        assertThat(authResponse.user().roles()).containsExactly(Role.CUSTOMER);

        // 2. Independently verify the JWT using ONLY the public key (simulating downstream resource server)
        NimbusJwtDecoder downstreamDecoder = NimbusJwtDecoder.withPublicKey(rsaKeyProvider.getPublicKey()).build();
        Jwt decodedJwt = downstreamDecoder.decode(authResponse.accessToken());

        assertThat(decodedJwt.getSubject()).isEqualTo(userId.toString());
        assertThat(decodedJwt.getClaimAsString("email")).isEqualTo(testUser.getEmail());
        assertThat(decodedJwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(decodedJwt.getIssuer().toString()).isEqualTo("http://identity-service:8080");
        assertThat(decodedJwt.getAudience()).contains("swiftdrop");

        // 3. Verify 15-minute expiration
        Instant iat = decodedJwt.getIssuedAt();
        Instant exp = decodedJwt.getExpiresAt();
        assertThat(iat).isNotNull();
        assertThat(exp).isNotNull();
        assertThat(Duration.between(iat, exp).getSeconds()).isEqualTo(900L);

        // 4. Verify refresh cookie
        Cookie cookie = result.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(604800);
    }

    @Test
    @DisplayName("Refresh: Rotates refresh token, issues new access token, and sets replaced_by linkage")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        // 1. Login to obtain initial token pair
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), rawPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie initialCookie = loginResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(initialCookie).isNotNull();
        String initialRawToken = initialCookie.getValue();

        // 2. Perform rotation via POST /api/v1/auth/refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(initialCookie))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andReturn();

        AuthResponse refreshResponse = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), AuthResponse.class);
        assertThat(refreshResponse.accessToken()).isNotBlank();
        assertThat(refreshResponse.user().id()).isEqualTo(userId);

        Cookie rotatedCookie = refreshResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(rotatedCookie).isNotNull();
        assertThat(rotatedCookie.getValue()).isNotBlank();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(initialRawToken);

        // 3. Verify in database: initial token is now revoked and replaced_by is set to rotated token ID
        RefreshToken rotatedEntity = tokenHashStorage.values().stream()
                .filter(t -> !t.isRevoked())
                .findFirst()
                .orElseThrow();

        RefreshToken initialEntity = tokenHashStorage.values().stream()
                .filter(RefreshToken::isRevoked)
                .findFirst()
                .orElseThrow();

        assertThat(initialEntity.isRevoked()).isTrue();
        assertThat(initialEntity.getReplacedBy()).isEqualTo(rotatedEntity.getId());
    }

    @Test
    @DisplayName("Replay Attack Detection: Replaying a rotated token revokes entire token family and returns 401")
    void shouldDetectReplayAttackAndRevokeAllTokensWhenRotatedTokenReused() throws Exception {
        // 1. Login
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), rawPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie token1Cookie = loginResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);

        // 2. Rotate once: token1 -> token2
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie))
                .andExpect(status().isOk())
                .andReturn();

        Cookie token2Cookie = refreshResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);

        // 3. Attacker replays token1!
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token1Cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value(ErrorCode.UNAUTHENTICATED.title()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));

        // 4. Token2 (legitimate user's token) must now ALSO be invalidated due to family revocation
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(token2Cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value(ErrorCode.UNAUTHENTICATED.title()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @DisplayName("Logout: Revokes refresh token and clears cookie with Max-Age=0; subsequent refresh fails")
    void shouldRevokeTokenAndClearCookieOnLogout() throws Exception {
        // 1. Login
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), rawPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);

        // 2. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        // 3. Attempt to refresh with the revoked token returns 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value(ErrorCode.UNAUTHENTICATED.title()))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @DisplayName("Refresh: Suspended user is rejected with 403 Forbidden")
    void shouldRejectSuspendedUserDuringTokenRefresh() throws Exception {
        // 1. Login as active user
        LoginRequest loginRequest = new LoginRequest(testUser.getEmail(), rawPassword);
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie cookie = loginResult.getResponse().getCookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);

        // 2. Suspend user
        testUser.setStatus(UserStatus.SUSPENDED);

        // 3. Attempt refresh -> 403
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value(ErrorCode.ACCESS_DENIED.title()))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }
}

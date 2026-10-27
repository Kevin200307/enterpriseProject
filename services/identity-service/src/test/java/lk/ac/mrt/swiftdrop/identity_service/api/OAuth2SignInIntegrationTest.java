package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.config.GoogleOAuth2Properties;
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
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleOAuth2Service;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleUserClaims;
import lk.ac.mrt.swiftdrop.identity_service.service.RateLimiterService;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.AuthServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.JwtServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.OAuth2AccountServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.OAuth2StateServiceImpl;
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

import java.time.Instant;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration slice tests for IDN-3 covering:
 * 1. Authorization initiation with PKCE and state token issuance
 * 2. Callback code exchange and Google ID token claims extraction
 * 3. Automatic user provisioning (CUSTOMER role, null password_hash, empty phone)
 * 4. RS256 JWT access token issuance and downstream signature verification
 * 5. HttpOnly, Secure, SameSite=Strict refresh token cookie transport
 * 6. Account linking to existing local password users
 * 7. Rejection of password login on Google-only users (401 INVALID_CREDENTIALS)
 * 8. Replay attack and state mismatch mitigation
 * 9. Conflict handling on mismatched google_sub
 * 10. Rejection of suspended accounts (403 ACCESS_DENIED)
 */
@WebMvcTest(controllers = {OAuth2Controller.class, AuthController.class})
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
        OAuth2StateServiceImpl.class,
        OAuth2AccountServiceImpl.class,
        OAuth2SignInIntegrationTest.TestPropertiesConfig.class
})
class OAuth2SignInIntegrationTest {

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
                    "swiftdrop-oauth-test-key"
            );
        }

        @Bean
        public GoogleOAuth2Properties googleOAuth2Properties() {
            return new GoogleOAuth2Properties(
                    "test-google-client-id.apps.googleusercontent.com",
                    "test-google-client-secret",
                    "http://localhost:5173/oauth/callback",
                    "https://accounts.google.com/o/oauth2/v2/auth",
                    "https://oauth2.googleapis.com/token",
                    "https://openidconnect.googleapis.com/v1/userinfo",
                    "https://www.googleapis.com/oauth2/v3/certs",
                    "https://accounts.google.com"
            );
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RsaKeyProvider rsaKeyProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean
    private GoogleOAuth2Service googleOAuth2Service;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private UserService userService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // RFC 7636 Appendix B test vector: codeChallenge = BASE64URL(SHA-256(ASCII(codeVerifier)))
    private static final String CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback";

    // In-memory stateful repository simulation
    private final Map<UUID, User> userStorage = new ConcurrentHashMap<>();
    private final Map<String, RefreshToken> refreshTokenStorage = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        userStorage.clear();
        refreshTokenStorage.clear();

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
                user.setCreatedAt(Instant.now());
            }
            user.setUpdatedAt(Instant.now());
            userStorage.put(user.getId(), user);
            return user;
        });

        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(invocation -> {
            String email = invocation.getArgument(0);
            return userStorage.values().stream()
                    .filter(u -> u.getEmail().equalsIgnoreCase(email))
                    .findFirst();
        });

        when(userRepository.findByGoogleSub(anyString())).thenAnswer(invocation -> {
            String sub = invocation.getArgument(0);
            return userStorage.values().stream()
                    .filter(u -> sub.equals(u.getGoogleSub()))
                    .findFirst();
        });

        when(roleRepository.findByRole(Role.CUSTOMER)).thenAnswer(invocation ->
                Optional.of(new RoleEntity(Role.CUSTOMER, "Customer role")));

        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            if (token.getId() == null) {
                token.setId(UUID.randomUUID());
            }
            refreshTokenStorage.put(token.getTokenHash(), token);
            return token;
        });
    }

    @Test
    @DisplayName("Complete end-to-end flow: authorize -> callback -> new user provisioned -> valid downstream JWT issued")
    void shouldExecuteFullGoogleOAuthSignInFlowAndVerifyJwtDownstream() throws Exception {
        // Step 1: Initiate OAuth2 authorization request
        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl", containsString("accounts.google.com")))
                .andExpect(jsonPath("$.authorizationUrl", containsString("code_challenge=" + CODE_CHALLENGE)))
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andReturn();

        JsonNode authNode = objectMapper.readTree(authResult.getResponse().getContentAsString());
        String state = authNode.get("state").asText();

        // Step 2: Simulate Google callback with authorization code
        String googleAuthCode = "google-auth-code-xyz";
        String googleSub = "118234567890123456789";
        String userEmail = "amandi.arangala@swiftdrop.lk";
        String userFullName = "Amandi Arangala";

        GoogleUserClaims claims = new GoogleUserClaims(
                googleSub,
                userEmail,
                true,
                userFullName,
                "Amandi",
                "Arangala",
                "https://lh3.googleusercontent.com/avatar.jpg"
        );
        when(googleOAuth2Service.exchangeAndVerify(googleAuthCode, CODE_VERIFIER, REDIRECT_URI))
                .thenReturn(claims);

        // Step 3: Call callback endpoint
        MvcResult callbackResult = mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", googleAuthCode)
                        .param("state", state)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value(userEmail))
                .andExpect(jsonPath("$.user.fullName").value(userFullName))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andReturn();

        // Step 4: Verify provisioned user in database storage
        assertThat(userStorage).hasSize(1);
        User provisionedUser = userStorage.values().iterator().next();
        assertThat(provisionedUser.getEmail()).isEqualTo(userEmail);
        assertThat(provisionedUser.getGoogleSub()).isEqualTo(googleSub);
        assertThat(provisionedUser.getPasswordHash()).isNull(); // Google-only users have null password
        assertThat(provisionedUser.getPhone()).isEqualTo("");
        assertThat(provisionedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(provisionedUser.hasRole(Role.CUSTOMER)).isTrue();

        // Step 5: Verify issued RS256 access token simulating downstream services
        JsonNode callbackNode = objectMapper.readTree(callbackResult.getResponse().getContentAsString());
        String accessToken = callbackNode.get("accessToken").asText();

        NimbusJwtDecoder downstreamDecoder = NimbusJwtDecoder.withPublicKey(rsaKeyProvider.getPublicKey()).build();
        Jwt decodedJwt = downstreamDecoder.decode(accessToken);

        assertThat(decodedJwt.getIssuer().toString()).isEqualTo("http://identity-service:8080");
        assertThat(decodedJwt.getSubject()).isEqualTo(provisionedUser.getId().toString());
        assertThat(decodedJwt.getClaimAsString("email")).isEqualTo(userEmail);
        List<String> rolesClaim = decodedJwt.getClaimAsStringList("roles");
        assertThat(rolesClaim).contains("CUSTOMER");
        assertThat(decodedJwt.getExpiresAt()).isAfter(Instant.now().plusSeconds(850));
    }

    @Test
    @DisplayName("Signing in twice with the same Google account does not create duplicate users")
    void shouldEnsureSigningInTwiceDoesNotCreateDuplicateUsers() throws Exception {
        String googleSub = "118234567890123456789";
        String userEmail = "repeat.customer@swiftdrop.lk";
        GoogleUserClaims claims = new GoogleUserClaims(
                googleSub,
                userEmail,
                true,
                "Repeat Customer",
                null,
                null,
                null
        );

        // Sign in attempt 1
        MvcResult auth1 = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state1 = objectMapper.readTree(auth1.getResponse().getContentAsString()).get("state").asText();

        when(googleOAuth2Service.exchangeAndVerify("code-1", CODE_VERIFIER, REDIRECT_URI)).thenReturn(claims);

        MvcResult callback1 = mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-1")
                        .param("state", state1)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String userId1 = objectMapper.readTree(callback1.getResponse().getContentAsString()).get("user").get("id").asText();
        assertThat(userStorage).hasSize(1);

        // Sign in attempt 2
        MvcResult auth2 = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state2 = objectMapper.readTree(auth2.getResponse().getContentAsString()).get("state").asText();

        when(googleOAuth2Service.exchangeAndVerify("code-2", CODE_VERIFIER, REDIRECT_URI)).thenReturn(claims);

        MvcResult callback2 = mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-2")
                        .param("state", state2)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String userId2 = objectMapper.readTree(callback2.getResponse().getContentAsString()).get("user").get("id").asText();

        // Proves: signing in twice preserves existing user and does not create duplicate entries
        assertThat(userId2).isEqualTo(userId1);
        assertThat(userStorage).hasSize(1);
    }

    @Test
    @DisplayName("Account linking: links google_sub to existing local password account matched by email")
    void shouldLinkGoogleSubToExistingPasswordUserWhenEmailMatches() throws Exception {
        // Pre-existing local password account
        String email = "existing.merchant@swiftdrop.lk";
        String rawPassword = "SecurePassword123!";
        User existingUser = new User(email, passwordEncoder.encode(rawPassword), "Existing Merchant", "+94771234567");
        existingUser.setId(UUID.randomUUID());
        existingUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));
        userStorage.put(existingUser.getId(), existingUser);

        // User initiates Google OAuth login
        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("state").asText();

        String googleSub = "google-sub-linked-999";
        GoogleUserClaims claims = new GoogleUserClaims(googleSub, email, true, "Existing Merchant", null, null, null);
        when(googleOAuth2Service.exchangeAndVerify("code-link", CODE_VERIFIER, REDIRECT_URI)).thenReturn(claims);

        // Callback links google_sub
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-link")
                        .param("state", state)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(existingUser.getId().toString()))
                .andExpect(jsonPath("$.user.email").value(email));

        // Verify google_sub is linked on the existing account
        User updated = userStorage.get(existingUser.getId());
        assertThat(updated.getGoogleSub()).isEqualTo(googleSub);
        assertThat(updated.getPhone()).isEqualTo("+94771234567");

        // Verify password hash is preserved and user can STILL log in via password
        LoginRequest loginRequest = new LoginRequest(email, rawPassword);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    @DisplayName("Google-only users with null password_hash must be rejected by password login with 401")
    void shouldRejectPasswordLoginForGoogleOnlyUserWithNullPassword() throws Exception {
        // Create user via Google OAuth (passwordHash == null)
        User googleOnlyUser = new User();
        googleOnlyUser.setId(UUID.randomUUID());
        googleOnlyUser.setEmail("googleonly@swiftdrop.lk");
        googleOnlyUser.setPasswordHash(null);
        googleOnlyUser.setFullName("Google Only User");
        googleOnlyUser.setPhone("");
        googleOnlyUser.setGoogleSub("google-sub-only-123");
        googleOnlyUser.setStatus(UserStatus.ACTIVE);
        googleOnlyUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));
        userStorage.put(googleOnlyUser.getId(), googleOnlyUser);

        // Attempt password login
        LoginRequest loginRequest = new LoginRequest("googleonly@swiftdrop.lk", "AnyPassword123!");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_CREDENTIALS.name()));
    }

    @Test
    @DisplayName("State parameter replay or mismatch is rejected with 401 UNAUTHENTICATED")
    void shouldRejectStateMismatchOrReplayAttack() throws Exception {
        // Authorize to get a valid state
        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String validState = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("state").asText();

        GoogleUserClaims claims = new GoogleUserClaims("sub-replay", "replay@swiftdrop.lk", true, "Replay", null, null, null);
        when(googleOAuth2Service.exchangeAndVerify(anyString(), anyString(), anyString())).thenReturn(claims);

        // Call callback 1 (consumes state)
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-replay-1")
                        .param("state", validState)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Call callback 2 with same state (replay attack)
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-replay-2")
                        .param("state", validState)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));

        // Call with non-existent / forged state
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-fake")
                        .param("state", "forged-state-token-12345")
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @DisplayName("PKCE code_verifier mismatch is rejected with 401 UNAUTHENTICATED")
    void shouldRejectPkceCodeVerifierMismatch() throws Exception {
        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("state").asText();

        // Tampered verifier that does not match S256 CODE_CHALLENGE
        String wrongVerifier = "tampered-wrong-verifier-12345678901234567890";

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-mismatch")
                        .param("state", state)
                        .param("codeVerifier", wrongVerifier)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.detail", containsString("PKCE code_verifier does not match code_challenge")));
    }

    @Test
    @DisplayName("If google_sub is already bound to a different email account, fail with 409 CONFLICT")
    void shouldRejectConflictingGoogleSubWhenAlreadyBoundToDifferentEmail() throws Exception {
        // Pre-existing account with google_sub
        User existingUser = new User();
        existingUser.setId(UUID.randomUUID());
        existingUser.setEmail("victim@swiftdrop.lk");
        existingUser.setGoogleSub("google-sub-shared");
        existingUser.setStatus(UserStatus.ACTIVE);
        userStorage.put(existingUser.getId(), existingUser);

        // Initiate flow
        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("state").asText();

        // Incoming claims use same google_sub but DIFFERENT email
        GoogleUserClaims conflictClaims = new GoogleUserClaims(
                "google-sub-shared",
                "attacker@swiftdrop.lk",
                true,
                "Attacker User",
                null,
                null,
                null
        );
        when(googleOAuth2Service.exchangeAndVerify("code-conflict", CODE_VERIFIER, REDIRECT_URI)).thenReturn(conflictClaims);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-conflict")
                        .param("state", state)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFLICT.name()))
                .andExpect(jsonPath("$.detail", containsString("Google account is already linked to another user account")));
    }

    @Test
    @DisplayName("Suspended accounts attempting Google sign-in are rejected with 403 ACCESS_DENIED")
    void shouldRejectSuspendedUserAttemptingGoogleSignIn() throws Exception {
        User suspendedUser = new User();
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.setEmail("suspended.user@swiftdrop.lk");
        suspendedUser.setGoogleSub("google-sub-suspended");
        suspendedUser.setStatus(UserStatus.SUSPENDED);
        userStorage.put(suspendedUser.getId(), suspendedUser);

        MvcResult authResult = mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", CODE_CHALLENGE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        String state = objectMapper.readTree(authResult.getResponse().getContentAsString()).get("state").asText();

        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-suspended",
                "suspended.user@swiftdrop.lk",
                true,
                "Suspended User",
                null,
                null,
                null
        );
        when(googleOAuth2Service.exchangeAndVerify("code-suspended", CODE_VERIFIER, REDIRECT_URI)).thenReturn(claims);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", "code-suspended")
                        .param("state", state)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }
}

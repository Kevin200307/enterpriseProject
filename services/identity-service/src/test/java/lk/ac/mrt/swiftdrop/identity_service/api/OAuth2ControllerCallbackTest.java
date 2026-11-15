package lk.ac.mrt.swiftdrop.identity_service.api;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.GoogleOAuth2Properties;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.ConflictException;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidOAuthStateException;
import lk.ac.mrt.swiftdrop.identity_service.error.OAuth2ExchangeException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleOAuth2Service;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleUserClaims;
import lk.ac.mrt.swiftdrop.identity_service.service.LoginResult;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2AccountService;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2StateData;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuth2Controller.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class OAuth2ControllerCallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GoogleOAuth2Properties googleOAuth2Properties;

    @MockitoBean
    private OAuth2StateService oAuth2StateService;

    @MockitoBean
    private GoogleOAuth2Service googleOAuth2Service;

    @MockitoBean
    private OAuth2AccountService oAuth2AccountService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String DEFAULT_REDIRECT_URI = "http://localhost:5173/oauth/callback";
    private static final String CODE = "google-auth-code-12345";
    private static final String STATE = "secure-random-state-token";
    private static final String CODE_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
    private static final String CODE_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

    @BeforeEach
    void setUp() {
        when(googleOAuth2Properties.redirectUri()).thenReturn(DEFAULT_REDIRECT_URI);
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 200 OK on successful authentication")
    void shouldSuccessfullyAuthenticateGoogleUserAndSetCookie() throws Exception {
        String customRedirectUri = "https://app.swiftdrop.lk/custom-callback";
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, customRedirectUri, Instant.now());
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-123",
                "customer@swiftdrop.lk",
                true,
                "Customer User",
                "Customer",
                "User",
                "https://lh3.googleusercontent.com/avatar.jpg"
        );

        UserResponse userResponse = new UserResponse(
                UUID.randomUUID(),
                "customer@swiftdrop.lk",
                "Customer User",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );
        AuthResponse authResponse = new AuthResponse("access.token.jwt", 900L, userResponse);
        LoginResult loginResult = new LoginResult(authResponse, "raw-refresh-token-abc");

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "raw-refresh-token-abc")
                .path("/api/v1/auth")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .maxAge(7 * 24 * 3600)
                .build();

        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);
        when(googleOAuth2Service.exchangeAndVerify(CODE, CODE_VERIFIER, customRedirectUri)).thenReturn(claims);
        when(oAuth2AccountService.authenticateGoogleUser(claims)).thenReturn(loginResult);
        when(authCookieService.createRefreshTokenCookie("raw-refresh-token-abc")).thenReturn(cookie);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .param("redirectUri", customRedirectUri)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=raw-refresh-token-abc")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.accessToken").value("access.token.jwt"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("customer@swiftdrop.lk"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 200 OK using default redirect URI when omitted")
    void shouldUseDefaultRedirectUriWhenNotProvided() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, DEFAULT_REDIRECT_URI, Instant.now());
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-default",
                "default@swiftdrop.lk",
                true,
                "Default User",
                null,
                null,
                null
        );

        UserResponse userResponse = new UserResponse(
                UUID.randomUUID(),
                "default@swiftdrop.lk",
                "Default User",
                "",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );
        AuthResponse authResponse = new AuthResponse("token.default.jwt", 900L, userResponse);
        LoginResult loginResult = new LoginResult(authResponse, "refresh-default");

        ResponseCookie cookie = ResponseCookie.from("refreshToken", "refresh-default").build();

        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);
        when(googleOAuth2Service.exchangeAndVerify(CODE, CODE_VERIFIER, DEFAULT_REDIRECT_URI)).thenReturn(claims);
        when(oAuth2AccountService.authenticateGoogleUser(claims)).thenReturn(loginResult);
        when(authCookieService.createRefreshTokenCookie("refresh-default")).thenReturn(cookie);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("token.default.jwt"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 400 Bad Request when code is missing")
    void shouldRejectMissingCodeWith400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[0].field").value("code"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 400 Bad Request when state is missing")
    void shouldRejectMissingStateWith400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[0].field").value("state"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 400 Bad Request when codeVerifier is missing")
    void shouldRejectMissingCodeVerifierWith400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[0].field").value("codeVerifier"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 401 Unauthorized when state is invalid, expired, or replayed")
    void shouldRejectInvalidOrConsumedStateWith401ProblemDetail() throws Exception {
        when(oAuth2StateService.consumeState(STATE))
                .thenThrow(new InvalidOAuthStateException("State token not found, expired, or already consumed"));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.detail", containsString("State token not found, expired, or already consumed")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 401 Unauthorized when PKCE codeVerifier does not match codeChallenge")
    void shouldRejectPkceMismatchWith401ProblemDetail() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, DEFAULT_REDIRECT_URI, Instant.now());
        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(false);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.detail", containsString("PKCE code_verifier does not match code_challenge")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 401 Unauthorized when redirectUri does not match authorization state")
    void shouldRejectRedirectUriMismatchWith401ProblemDetail() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, "https://legitimate.lk/callback", Instant.now());
        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .param("redirectUri", "https://tampered.lk/callback")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.detail", containsString("OAuth2 redirect_uri mismatch")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 401 Unauthorized when Google token exchange fails")
    void shouldRejectGoogleTokenExchangeFailureWith401ProblemDetail() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, DEFAULT_REDIRECT_URI, Instant.now());
        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);
        when(googleOAuth2Service.exchangeAndVerify(CODE, CODE_VERIFIER, DEFAULT_REDIRECT_URI))
                .thenThrow(new OAuth2ExchangeException("Google token exchange failed: 400 BAD_REQUEST"));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.detail", containsString("Google token exchange failed: 400 BAD_REQUEST")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 409 Conflict when account linking conflict occurs")
    void shouldRejectAccountLinkingConflictWith409ProblemDetail() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, DEFAULT_REDIRECT_URI, Instant.now());
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-conflict",
                "conflict@swiftdrop.lk",
                true,
                "Conflict User",
                null,
                null,
                null
        );

        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);
        when(googleOAuth2Service.exchangeAndVerify(CODE, CODE_VERIFIER, DEFAULT_REDIRECT_URI)).thenReturn(claims);
        when(oAuth2AccountService.authenticateGoogleUser(claims))
                .thenThrow(new ConflictException("Google account is already linked to another user account"));

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFLICT.name()))
                .andExpect(jsonPath("$.detail", containsString("Google account is already linked to another user account")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/callback/google - 403 Forbidden when linked account is suspended")
    void shouldRejectSuspendedUserWith403ProblemDetail() throws Exception {
        OAuth2StateData stateData = new OAuth2StateData(CODE_CHALLENGE, DEFAULT_REDIRECT_URI, Instant.now());
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-suspended",
                "suspended@swiftdrop.lk",
                true,
                "Suspended User",
                null,
                null,
                null
        );

        when(oAuth2StateService.consumeState(STATE)).thenReturn(stateData);
        when(oAuth2StateService.validatePkce(CODE_VERIFIER, CODE_CHALLENGE)).thenReturn(true);
        when(googleOAuth2Service.exchangeAndVerify(CODE, CODE_VERIFIER, DEFAULT_REDIRECT_URI)).thenReturn(claims);
        when(oAuth2AccountService.authenticateGoogleUser(claims))
                .thenThrow(new UserSuspendedException());

        mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                        .param("code", CODE)
                        .param("state", STATE)
                        .param("codeVerifier", CODE_VERIFIER)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }
}

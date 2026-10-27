package lk.ac.mrt.swiftdrop.identity_service.api;

import jakarta.servlet.http.Cookie;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import lk.ac.mrt.swiftdrop.identity_service.service.TokenRotationResult;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
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

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerRefreshTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private User testUser;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        testUser = new User("amandi.arangala@swiftdrop.lk", "$2b$10$hash", "Amandi Arangala", "+94771234567");
        testUser.setId(userId);
        testUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer role"));
        testUser.setCreatedAt(Instant.now());
        testUser.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 200 OK with rotated access token and new refresh cookie")
    void shouldRotateTokenSuccessfullyWithCookie() throws Exception {
        String presentingToken = "presenting-raw-refresh-token";
        String rotatedToken = "rotated-raw-refresh-token";
        String newAccessToken = "new-rs256-access-token";

        TokenRotationResult rotationResult = new TokenRotationResult(newAccessToken, rotatedToken, 900L, testUser);

        when(refreshTokenService.rotate(presentingToken)).thenReturn(rotationResult);
        when(authCookieService.createRefreshTokenCookie(rotatedToken)).thenReturn(
                ResponseCookie.from(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, rotatedToken)
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path(AuthCookieService.AUTH_COOKIE_PATH)
                        .maxAge(Duration.ofSeconds(604800))
                        .build()
        );

        Cookie refreshCookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, presentingToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=" + rotatedToken)))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(jsonPath("$.accessToken").value(newAccessToken))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 401 Unauthorized when refresh cookie is missing")
    void shouldReturn401WhenRefreshCookieMissing() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(ProblemFields.TYPE).value(ErrorCode.UNAUTHENTICATED.type().toString()))
                .andExpect(jsonPath(ProblemFields.ERROR_CODE).value("UNAUTHENTICATED"))
                .andExpect(jsonPath(ProblemFields.DETAIL).value("Refresh token cookie is missing"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 401 Unauthorized when refresh cookie is blank")
    void shouldReturn401WhenRefreshCookieBlank() throws Exception {
        Cookie blankCookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, "   ");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(blankCookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(ProblemFields.TYPE).value(ErrorCode.UNAUTHENTICATED.type().toString()))
                .andExpect(jsonPath(ProblemFields.ERROR_CODE).value("UNAUTHENTICATED"))
                .andExpect(jsonPath(ProblemFields.DETAIL).value("Refresh token cookie is missing"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 401 Unauthorized when token is expired")
    void shouldReturn401WhenTokenIsExpired() throws Exception {
        String expiredToken = "expired-refresh-token";
        Cookie cookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, expiredToken);

        when(refreshTokenService.rotate(expiredToken))
                .thenThrow(new InvalidTokenException("Refresh token has expired"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(ProblemFields.TYPE).value(ErrorCode.UNAUTHENTICATED.type().toString()))
                .andExpect(jsonPath(ProblemFields.ERROR_CODE).value("UNAUTHENTICATED"))
                .andExpect(jsonPath(ProblemFields.DETAIL).value("Refresh token has expired"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 401 Unauthorized on replay attack detection")
    void shouldReturn401OnReplayAttack() throws Exception {
        String replayedToken = "replayed-revoked-token";
        Cookie cookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, replayedToken);

        when(refreshTokenService.rotate(replayedToken))
                .thenThrow(new InvalidTokenException("Refresh token has already been used. Session invalidated for security."));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(ProblemFields.TYPE).value(ErrorCode.UNAUTHENTICATED.type().toString()))
                .andExpect(jsonPath(ProblemFields.ERROR_CODE).value("UNAUTHENTICATED"))
                .andExpect(jsonPath(ProblemFields.DETAIL).value(containsString("already been used")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh - 403 Forbidden when user account is suspended")
    void shouldReturn403WhenUserIsSuspended() throws Exception {
        String validToken = "valid-token-for-suspended-user";
        Cookie cookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, validToken);

        when(refreshTokenService.rotate(validToken))
                .thenThrow(new UserSuspendedException());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(cookie)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(ProblemFields.TYPE).value(ErrorCode.ACCESS_DENIED.type().toString()))
                .andExpect(jsonPath(ProblemFields.ERROR_CODE).value("ACCESS_DENIED"));
    }
}

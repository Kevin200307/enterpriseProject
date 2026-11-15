package lk.ac.mrt.swiftdrop.identity_service.api;

import jakarta.servlet.http.Cookie;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerLogoutTest {

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

    @Test
    @DisplayName("POST /api/v1/auth/logout - 204 No Content with token revocation and cookie clearance")
    void shouldRevokeTokenAndClearCookieWhenRefreshTokenPresent() throws Exception {
        String presentingToken = "valid-raw-refresh-token-to-revoke";

        when(authCookieService.createClearCookie()).thenReturn(
                ResponseCookie.from(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path(AuthCookieService.AUTH_COOKIE_PATH)
                        .maxAge(Duration.ZERO)
                        .build()
        );

        Cookie cookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, presentingToken);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(cookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));

        verify(refreshTokenService).revokeToken(presentingToken);
        verify(authCookieService).createClearCookie();
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - 204 No Content idempotent clearance when no cookie provided")
    void shouldClearCookieWhenNoRefreshTokenCookiePresent() throws Exception {
        when(authCookieService.createClearCookie()).thenReturn(
                ResponseCookie.from(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path(AuthCookieService.AUTH_COOKIE_PATH)
                        .maxAge(Duration.ZERO)
                        .build()
        );

        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(refreshTokenService, never()).revokeToken(anyString());
        verify(authCookieService).createClearCookie();
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout - 204 No Content idempotent clearance when blank cookie provided")
    void shouldClearCookieWhenRefreshTokenCookieIsBlank() throws Exception {
        when(authCookieService.createClearCookie()).thenReturn(
                ResponseCookie.from(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, "")
                        .httpOnly(true)
                        .secure(true)
                        .sameSite("Strict")
                        .path(AuthCookieService.AUTH_COOKIE_PATH)
                        .maxAge(Duration.ZERO)
                        .build()
        );

        Cookie blankCookie = new Cookie(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, "   ");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(blankCookie))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        verify(refreshTokenService, never()).revokeToken(anyString());
        verify(authCookieService).createClearCookie();
    }
}

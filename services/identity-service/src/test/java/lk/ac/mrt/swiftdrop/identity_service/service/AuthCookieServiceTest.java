package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookieServiceTest {

    private AuthCookieService authCookieService;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties(
                "http://identity-service:8080",
                "swiftdrop",
                900,
                604800, // 7 days
                "",
                "",
                "swiftdrop-v1"
        );
        authCookieService = new AuthCookieService(jwtProperties);
    }

    @Test
    @DisplayName("Should create refresh token cookie with HttpOnly, Secure, SameSite=Strict, and 7-day TTL")
    void shouldCreateRefreshTokenCookieWithStrictSecurityAttributes() {
        String rawToken = "secure-opaque-refresh-token-xyz123";

        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(rawToken);

        assertThat(cookie.getName()).isEqualTo(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo(rawToken);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofSeconds(604800));
    }

    @Test
    @DisplayName("Should create clearance cookie with Max-Age 0 to purge cookie on logout")
    void shouldCreateClearanceCookieWithZeroMaxAge() {
        ResponseCookie cookie = authCookieService.createClearCookie();

        assertThat(cookie.getName()).isEqualTo(AuthCookieService.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
    }
}

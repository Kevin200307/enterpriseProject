package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service generating and managing secure HttpOnly cookies for refresh token transport (§4.3).
 * Enforces Path='/api/v1/auth', SameSite='Strict', HttpOnly, and Secure flags.
 */
@Service
public class AuthCookieService {

    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";
    public static final String AUTH_COOKIE_PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;

    public AuthCookieService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * Creates an HttpOnly, Secure, SameSite=Strict cookie carrying the opaque refresh token.
     * Path is restricted to '/api/v1/auth' to cover refresh and logout endpoints.
     *
     * @param rawRefreshToken the raw unhashed refresh token
     * @return configured ResponseCookie
     */
    public ResponseCookie createRefreshTokenCookie(String rawRefreshToken) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, rawRefreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(jwtProperties.refreshTokenTtlSeconds()))
                .build();
    }

    /**
     * Creates a clearance cookie with maxAge(0) to purge the refresh token upon logout.
     *
     * @return clearance ResponseCookie
     */
    public ResponseCookie createClearCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AUTH_COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }
}

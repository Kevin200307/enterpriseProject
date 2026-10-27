package lk.ac.mrt.swiftdrop.identity_service.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.LoginResult;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import lk.ac.mrt.swiftdrop.identity_service.service.TokenRotationResult;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * REST controller exposing user registration and authentication endpoints.
 * All public endpoints are under the '/api/v1/auth' prefix.
 */
@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication", description = "User registration, password login, token refresh, and logout")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final AuthCookieService authCookieService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            UserService userService,
            AuthService authService,
            AuthCookieService authCookieService,
            RefreshTokenService refreshTokenService
    ) {
        this.userService = userService;
        this.authService = authService;
        this.authCookieService = authCookieService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping(path = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Register a new customer account",
            description = "Creates a new user with CUSTOMER role and BCrypt-hashed password. Returns 201 with Location header."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Customer successfully registered",
                    headers = @Header(name = "Location", description = "URI of the created user profile resource", schema = @Schema(type = "string"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email already registered (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = userService.register(request);
        URI location = URI.create("/api/v1/users/" + response.id());
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping(path = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Authenticate user with email and password",
            description = "Validates user credentials with anti-timing attack defenses and rate limiting. Issues RS256 access token in body and refresh token in HttpOnly cookie."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully authenticated",
                    headers = @Header(name = "Set-Cookie", description = "HttpOnly, Secure, SameSite=Strict refresh token cookie", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account suspended (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Rate limit exceeded (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResult result = authService.login(request);
        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(result.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.authResponse());
    }

    @PostMapping(path = "/refresh")
    @Operation(
            summary = "Rotate refresh token and issue new access token",
            description = "Validates the refresh token from the HttpOnly cookie, rotates the token pair with replay attack detection, and sets a new HttpOnly refresh cookie."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully rotated refresh token",
                    headers = @Header(name = "Set-Cookie", description = "HttpOnly, Secure, SameSite=Strict rotated refresh token cookie", schema = @Schema(type = "string")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid, expired, missing, or replayed refresh token (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account suspended (RFC 7807)",
                    content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = AuthCookieService.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token cookie is missing");
        }
        TokenRotationResult result = refreshTokenService.rotate(refreshToken);
        ResponseCookie cookie = authCookieService.createRefreshTokenCookie(result.rawRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.toAuthResponse());
    }
}

package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidCredentialsException;
import lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerLoginTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST /api/v1/auth/login - 200 OK with authenticated user profile")
    void shouldLoginSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        LoginRequest request = new LoginRequest("amandi.arangala@swiftdrop.lk", "Password123!");

        UserResponse userResponse = new UserResponse(
                userId,
                "amandi.arangala@swiftdrop.lk",
                "Amandi Arangala",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(new AuthResponse("signed-token", 900, userResponse));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 401 Unauthorized on invalid credentials (same error for wrong user or password)")
    void shouldReturn401OnInvalidCredentials() throws Exception {
        LoginRequest request = new LoginRequest("wrong@swiftdrop.lk", "WrongPassword!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_CREDENTIALS.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.INVALID_CREDENTIALS.title()))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 403 Forbidden when account is suspended")
    void shouldReturn403WhenAccountIsSuspended() throws Exception {
        LoginRequest request = new LoginRequest("suspended@swiftdrop.lk", "Password123!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UserSuspendedException());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.ACCESS_DENIED.title()))
                .andExpect(jsonPath("$.detail").value("User account has been suspended"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 429 Too Many Requests with Retry-After header when rate limit exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        LoginRequest request = new LoginRequest("bruteforce@swiftdrop.lk", "AnyPassword!");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new RateLimitExceededException(45));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "45"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RATE_LIMITED.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.RATE_LIMITED.title()))
                .andExpect(jsonPath("$.detail").value(containsString("Too many requests")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login - 400 Bad Request with violations when email or password is missing")
    void shouldReturn400OnInvalidLoginPayload() throws Exception {
        LoginRequest invalidRequest = new LoginRequest("not-an-email", "");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[*].field", hasItems("email", "password")));
    }
}

package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.DuplicateEmailException;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthCookieService;
import lk.ac.mrt.swiftdrop.identity_service.service.AuthService;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class AuthControllerRegistrationTest {
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private AuthCookieService authCookieService;

    @Test
    @DisplayName("POST /api/v1/auth/register - 201 Created with Location header and user body")
    void shouldRegisterSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(
                "amandi.arangala@swiftdrop.lk",
                "StrongPassword123",
                "Amandi Arangala",
                "+94771234567"
        );

        UserResponse response = new UserResponse(
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

        when(userService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + userId))
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.fullName").value("Amandi Arangala"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 409 Conflict when email is already registered")
    void shouldReturn409OnDuplicateEmail() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "existing@swiftdrop.lk",
                "StrongPassword123",
                "Existing User",
                "+94771234567"
        );

        when(userService.register(any(RegisterRequest.class)))
                .thenThrow(new DuplicateEmailException());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFLICT.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.CONFLICT.title()))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register - 400 Bad Request with violations when payload is invalid")
    void shouldReturn400OnInvalidPayload() throws Exception {
        RegisterRequest invalidRequest = new RegisterRequest(
                "invalid-email",
                "short",      // less than 8 chars
                "",           // empty name
                "0771234567"  // missing +94 prefix
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[*].field", org.hamcrest.Matchers.hasItems("email", "password", "fullName", "phone")));
    }
}

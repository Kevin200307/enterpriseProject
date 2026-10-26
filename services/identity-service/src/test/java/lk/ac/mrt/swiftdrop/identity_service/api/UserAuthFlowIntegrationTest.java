package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityBeansConfig;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.RateLimiterService;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtTokenService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.AuthServiceImpl;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end slice test exercising the full user lifecycle:
 * Registration -> BCrypt password hashing -> Password login -> Rate limiting -> Authenticated profile retrieval and updates.
 * Proves IDN-1 acceptance criteria.
 */
@WebMvcTest(controllers = {AuthController.class, UserController.class})
@Import({
        GlobalExceptionHandler.class,
        SecurityConfig.class,
        SecurityBeansConfig.class,
        UserServiceImpl.class,
        AuthServiceImpl.class
})
class UserAuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private JwtEncoder jwtEncoder;

    @Test
    @DisplayName("Full Lifecycle: Register customer -> Login with BCrypt -> Get profile -> Update profile")
    void shouldCompleteFullRegistrationLoginAndProfileLifecycle() throws Exception {
        RoleEntity customerRole = new RoleEntity(Role.CUSTOMER, "Customer role");
        when(roleRepository.findByRole(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);

        AtomicReference<User> savedUserHolder = new AtomicReference<>();
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(UUID.randomUUID());
            }
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            savedUserHolder.set(user);
            return user;
        });

        // 1. REGISTER NEW CUSTOMER
        RegisterRequest registerRequest = new RegisterRequest(
                "amandi.arangala@swiftdrop.lk",
                "StrongPassword123!",
                "Amandi Arangala",
                "+94771234567"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/users/")))
                .andExpect(jsonPath("$.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.fullName").value("Amandi Arangala"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                // Security verification: password hash is NEVER leaked in response
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        User registeredUser = savedUserHolder.get();
        assertThat(registeredUser).isNotNull();
        // Verify real BCrypt strength 10 encoding
        assertThat(registeredUser.getPasswordHash()).startsWith("$2a$10$");
        assertThat(registeredUser.getPasswordHash()).isNotEqualTo("StrongPassword123!");

        // 2. PASSWORD LOGIN WITH REAL BCRYPT VERIFICATION
        when(userRepository.findByEmailIgnoreCase("amandi.arangala@swiftdrop.lk"))
                .thenReturn(Optional.of(registeredUser));

        LoginRequest loginRequest = new LoginRequest("amandi.arangala@swiftdrop.lk", "StrongPassword123!");
        when(jwtTokenService.createAccessToken(registeredUser)).thenReturn("signed-token");
        when(jwtTokenService.expiresIn()).thenReturn(900L);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed-token"))
                .andExpect(jsonPath("$.user.id").value(registeredUser.getId().toString()))
                .andExpect(jsonPath("$.user.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.user.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());

        verify(rateLimiterService).checkRateLimit("amandi.arangala@swiftdrop.lk");
        verify(rateLimiterService).resetAttempts("amandi.arangala@swiftdrop.lk");

        // 3. GET AUTHENTICATED USER PROFILE (/api/v1/users/me)
        when(userRepository.findById(registeredUser.getId())).thenReturn(Optional.of(registeredUser));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                registeredUser.getId().toString(),
                "n/a",
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );

        mockMvc.perform(get("/api/v1/users/me")
                        .with(authentication(auth))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(registeredUser.getId().toString()))
                .andExpect(jsonPath("$.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.fullName").value("Amandi Arangala"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        // 4. PATCH AUTHENTICATED USER PROFILE (/api/v1/users/me)
        UpdateProfileRequest updateRequest = new UpdateProfileRequest("Amandi Updated", "+94779876543");

        mockMvc.perform(patch("/api/v1/users/me")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Amandi Updated"))
                .andExpect(jsonPath("$.phone").value("+94779876543"));

        assertThat(registeredUser.getFullName()).isEqualTo("Amandi Updated");
        assertThat(registeredUser.getPhone()).isEqualTo("+94779876543");
    }

    @Test
    @DisplayName("Registration: 409 Conflict when email already exists")
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        when(userRepository.existsByEmailIgnoreCase("existing@swiftdrop.lk")).thenReturn(true);

        RegisterRequest request = new RegisterRequest(
                "existing@swiftdrop.lk",
                "Password123!",
                "Existing User",
                "+94771234567"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.CONFLICT.name()))
                .andExpect(jsonPath("$.detail").value(containsString("already exists")));
    }

    @Test
    @DisplayName("Registration: 400 Bad Request with field violations on invalid inputs")
    void shouldRejectInvalidRegistrationInputs() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "not-an-email",
                "short",      // less than 8 chars
                "",           // blank name
                "0771234567"  // invalid phone missing +94
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[*].field", hasItems("email", "password", "fullName", "phone")));
    }

    @Test
    @DisplayName("Login: 401 INVALID_CREDENTIALS for both missing user and wrong password (anti-enumeration)")
    void shouldReturnSame401ForMissingUserAndWrongPassword() throws Exception {
        // Case A: Missing user
        when(userRepository.findByEmailIgnoreCase("missing@swiftdrop.lk")).thenReturn(Optional.empty());

        LoginRequest missingUserReq = new LoginRequest("missing@swiftdrop.lk", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(missingUserReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_CREDENTIALS.name()))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        verify(rateLimiterService).recordFailedAttempt("missing@swiftdrop.lk");

        // Case B: Existing user with wrong password
        String validHash = passwordEncoder.encode("RealSecretPassword123!");
        User user = new User("valid@swiftdrop.lk", validHash, "Valid User", "+94771234567");
        user.setId(UUID.randomUUID());
        user.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));
        when(userRepository.findByEmailIgnoreCase("valid@swiftdrop.lk")).thenReturn(Optional.of(user));

        LoginRequest wrongPasswordReq = new LoginRequest("valid@swiftdrop.lk", "WrongPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPasswordReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.INVALID_CREDENTIALS.name()))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        verify(rateLimiterService).recordFailedAttempt("valid@swiftdrop.lk");
    }

    @Test
    @DisplayName("Login: 429 Too Many Requests with Retry-After header when rate limit is exceeded")
    void shouldEnforceRateLimitOnLogin() throws Exception {
        doThrow(new RateLimitExceededException(50))
                .when(rateLimiterService).checkRateLimit("bruteforce@swiftdrop.lk");

        LoginRequest request = new LoginRequest("bruteforce@swiftdrop.lk", "AnyPassword!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "50"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RATE_LIMITED.name()));
    }

    @Test
    @DisplayName("Login: 403 Forbidden when user account is suspended")
    void shouldRejectSuspendedAccountOnLogin() throws Exception {
        String suspendedHash = passwordEncoder.encode("ValidPassword123!");
        User suspendedUser = new User("suspended@swiftdrop.lk", suspendedHash, "Suspended User", "+94771234567");
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.suspend();
        suspendedUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));

        when(userRepository.findByEmailIgnoreCase("suspended@swiftdrop.lk")).thenReturn(Optional.of(suspendedUser));

        LoginRequest request = new LoginRequest("suspended@swiftdrop.lk", "ValidPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()))
                .andExpect(jsonPath("$.detail").value("User account has been suspended"));
    }

    @Test
    @DisplayName("Profile: 401 Unauthorized when accessing /api/v1/users/me unauthenticated")
    void shouldRejectUnauthenticatedProfileAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateProfileRequest("New Name", "+94771234567"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @WithMockUser(username = "b1c0e3a6-2d12-4217-91a9-df56e9c9b101", roles = {"CUSTOMER"})
    @DisplayName("Profile: 400 Bad Request on invalid phone number format")
    void shouldRejectInvalidProfileUpdatePayload() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Valid Name", "0771234567"); // not international

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[0].field").value("phone"));
    }
}

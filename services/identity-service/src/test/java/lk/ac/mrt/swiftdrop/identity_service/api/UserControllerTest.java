package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.error.ResourceNotFoundException;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class UserControllerTest {
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @MockitoBean
    private UserService userService;

    private static final String USER_ID_STRING = "b1c0e3a6-2d12-4217-91a9-df56e9c9b101";
    private static final UUID USER_ID = UUID.fromString(USER_ID_STRING);

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/users/me - 200 OK with authenticated user profile")
    void shouldGetCurrentUserProfileWhenAuthenticated() throws Exception {
        UserResponse userResponse = new UserResponse(
                USER_ID,
                "amandi.arangala@swiftdrop.lk",
                "Amandi Arangala",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );

        when(userService.getUserProfile(USER_ID)).thenReturn(userResponse);

        mockMvc.perform(get("/api/v1/users/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID_STRING))
                .andExpect(jsonPath("$.email").value("amandi.arangala@swiftdrop.lk"))
                .andExpect(jsonPath("$.fullName").value("Amandi Arangala"))
                .andExpect(jsonPath("$.phone").value("+94771234567"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/v1/users/me - 401 Unauthorized when request is unauthenticated")
    void shouldReturn401WhenGetMeUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.UNAUTHENTICATED.title()));
    }

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/users/me - 404 Not Found when authenticated user does not exist in database")
    void shouldReturn404WhenUserNotFound() throws Exception {
        when(userService.getUserProfile(USER_ID))
                .thenThrow(new ResourceNotFoundException("User", USER_ID));

        mockMvc.perform(get("/api/v1/users/me")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.RESOURCE_NOT_FOUND.title()));
    }

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = {"CUSTOMER"})
    @DisplayName("PATCH /api/v1/users/me - 200 OK with updated profile")
    void shouldUpdateCurrentUserProfileWhenAuthenticatedAndValid() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Amandi Updated", "+94779876543");
        UserResponse updatedResponse = new UserResponse(
                USER_ID,
                "amandi.arangala@swiftdrop.lk",
                "Amandi Updated",
                "+94779876543",
                UserStatus.ACTIVE,
                Set.of(Role.CUSTOMER),
                false,
                Instant.now(),
                Instant.now()
        );

        when(userService.updateProfile(eq(USER_ID), any(UpdateProfileRequest.class)))
                .thenReturn(updatedResponse);

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Amandi Updated"))
                .andExpect(jsonPath("$.phone").value("+94779876543"));
    }

    @Test
    @WithMockUser(username = USER_ID_STRING, roles = {"CUSTOMER"})
    @DisplayName("PATCH /api/v1/users/me - 400 Bad Request when update payload fails validation")
    void shouldReturn400WhenUpdateProfileValidationFails() throws Exception {
        UpdateProfileRequest invalidRequest = new UpdateProfileRequest("A", "0771234567");

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[*].field", hasItems("fullName", "phone")));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me - 401 Unauthorized when update request is unauthenticated")
    void shouldReturn401WhenPatchMeUnauthenticated() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Amandi Updated", "+94779876543");

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("GET /api/v1/users - 200 OK with paginated users for ADMIN")
    void shouldListUsersForAdmin() throws Exception {
        UserResponse userResponse = new UserResponse(
                USER_ID,
                "courier@swiftdrop.lk",
                "Swift Courier",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of(Role.COURIER),
                false,
                Instant.now(),
                Instant.now()
        );
        PageImpl<UserResponse> page = new PageImpl<>(List.of(userResponse), PageRequest.of(0, 10), 1);

        when(userService.listUsers(eq("COURIER"), eq(UserStatus.ACTIVE), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/users")
                        .param("role", "COURIER")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(USER_ID_STRING))
                .andExpect(jsonPath("$.content[0].email").value("courier@swiftdrop.lk"))
                .andExpect(jsonPath("$.content[0].fullName").value("Swift Courier"))
                .andExpect(jsonPath("$.content[0].roles[0]").value("COURIER"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/users - 403 Forbidden for non-ADMIN user")
    void shouldReturn403WhenNonAdminListsUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.ACCESS_DENIED.title()));
    }

    @Test
    @DisplayName("GET /api/v1/users - 401 Unauthorized when request is unauthenticated")
    void shouldReturn401WhenListUsersUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()))
                .andExpect(jsonPath("$.title").value(ErrorCode.UNAUTHENTICATED.title()));
    }
}

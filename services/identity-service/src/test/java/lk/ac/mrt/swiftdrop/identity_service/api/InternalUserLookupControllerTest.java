package lk.ac.mrt.swiftdrop.identity_service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.BatchUserLookupRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserLookupResponse;
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
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InternalUserLookupController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class InternalUserLookupControllerTest {

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
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("POST /api/v1/internal/users/lookup - 200 OK for ADMIN caller")
    void shouldBatchLookupUsersForAdmin() throws Exception {
        BatchUserLookupRequest request = new BatchUserLookupRequest(List.of(USER_ID));
        UserLookupResponse response = new UserLookupResponse(
                USER_ID,
                "user@swiftdrop.lk",
                "Swift User",
                "Swift User",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of("CUSTOMER")
        );

        when(userService.lookupUsers(eq(List.of(USER_ID)))).thenReturn(List.of(response));

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(USER_ID_STRING))
                .andExpect(jsonPath("$[0].email").value("user@swiftdrop.lk"))
                .andExpect(jsonPath("$[0].fullName").value("Swift User"))
                .andExpect(jsonPath("$[0].name").value("Swift User"))
                .andExpect(jsonPath("$[0].roles[0]").value("CUSTOMER"));
    }

    @Test
    @WithMockUser(authorities = {"SCOPE_internal"})
    @DisplayName("POST /api/v1/internal/users/lookup - 200 OK for internal service token (SCOPE_internal)")
    void shouldBatchLookupUsersForInternalService() throws Exception {
        BatchUserLookupRequest request = new BatchUserLookupRequest(List.of(USER_ID));
        UserLookupResponse response = new UserLookupResponse(
                USER_ID,
                "service@swiftdrop.lk",
                "Service Account",
                "Service Account",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of("DISPATCHER")
        );

        when(userService.lookupUsers(any())).thenReturn(List.of(response));

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(USER_ID_STRING));
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    @DisplayName("POST /api/v1/internal/users/lookup - 403 Forbidden for CUSTOMER role")
    void shouldReturn403WhenCustomerCallsBatchLookup() throws Exception {
        BatchUserLookupRequest request = new BatchUserLookupRequest(List.of(USER_ID));

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    @DisplayName("POST /api/v1/internal/users/lookup - 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenBatchLookupUnauthenticated() throws Exception {
        BatchUserLookupRequest request = new BatchUserLookupRequest(List.of(USER_ID));

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("POST /api/v1/internal/users/lookup - 400 Bad Request when userIds is empty")
    void shouldReturn400WhenBatchLookupUserIdsEmpty() throws Exception {
        String invalidPayload = "{\"userIds\": []}";

        mockMvc.perform(post("/api/v1/internal/users/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("GET /api/v1/internal/users/lookup - 200 OK for single user lookup")
    void shouldLookupSingleUserForAdmin() throws Exception {
        UserLookupResponse response = new UserLookupResponse(
                USER_ID,
                "single@swiftdrop.lk",
                "Single User",
                "Single User",
                "+94771234567",
                UserStatus.ACTIVE,
                Set.of("COURIER")
        );

        when(userService.lookupUser(USER_ID)).thenReturn(response);

        mockMvc.perform(get("/api/v1/internal/users/lookup")
                        .param("id", USER_ID_STRING)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID_STRING))
                .andExpect(jsonPath("$.email").value("single@swiftdrop.lk"))
                .andExpect(jsonPath("$.fullName").value("Single User"))
                .andExpect(jsonPath("$.name").value("Single User"))
                .andExpect(jsonPath("$.phone").value("+94771234567"));
    }

    @Test
    @WithMockUser(roles = {"ADMIN"})
    @DisplayName("GET /api/v1/internal/users/lookup - 404 Not Found when user does not exist")
    void shouldReturn404WhenSingleUserNotFound() throws Exception {
        when(userService.lookupUser(USER_ID)).thenThrow(new ResourceNotFoundException("User", USER_ID));

        mockMvc.perform(get("/api/v1/internal/users/lookup")
                        .param("id", USER_ID_STRING)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.RESOURCE_NOT_FOUND.name()));
    }

    @Test
    @WithMockUser(roles = {"CUSTOMER"})
    @DisplayName("GET /api/v1/internal/users/lookup - 403 Forbidden for CUSTOMER role")
    void shouldReturn403WhenCustomerCallsSingleLookup() throws Exception {
        mockMvc.perform(get("/api/v1/internal/users/lookup")
                        .param("id", USER_ID_STRING)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.ACCESS_DENIED.name()));
    }

    @Test
    @DisplayName("GET /api/v1/internal/users/lookup - 401 Unauthorized when unauthenticated")
    void shouldReturn401WhenSingleLookupUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/internal/users/lookup")
                        .param("id", USER_ID_STRING)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.UNAUTHENTICATED.name()));
    }
}

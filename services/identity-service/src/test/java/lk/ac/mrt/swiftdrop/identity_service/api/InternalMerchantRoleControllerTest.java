package lk.ac.mrt.swiftdrop.identity_service.api;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalMerchantRoleController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class InternalMerchantRoleControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UserService userService;
    @MockitoBean JwtDecoder jwtDecoder;

    private static final UUID USER_ID = UUID.fromString("b1c0e3a6-2d12-4217-91a9-df56e9c9b101");

    @Test
    void grantsRoleForExactMerchantServiceScope() throws Exception {
        when(userService.grantMerchantRole(USER_ID)).thenReturn(new UserResponse(
                USER_ID, "merchant@example.com", "Merchant", "+94770000000",
                UserStatus.ACTIVE, Set.of(Role.CUSTOMER, Role.MERCHANT), false, Instant.EPOCH, Instant.EPOCH));

        mockMvc.perform(post("/api/v1/internal/users/{id}/merchant-role", USER_ID)
                        .with(jwt().jwt(j -> j.claim("client_id", "merchant-service")
                                .claim("scope", "merchant.internal"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    void rejectsWrongClient() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/{id}/merchant-role", USER_ID)
                        .with(jwt().jwt(j -> j.claim("client_id", "dispatch-service")
                                .claim("scope", "merchant.internal"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWrongScope() throws Exception {
        mockMvc.perform(post("/api/v1/internal/users/{id}/merchant-role", USER_ID)
                        .with(jwt().jwt(j -> j.claim("client_id", "merchant-service")
                                .claim("scope", "dispatch.internal"))))
                .andExpect(status().isForbidden());
    }
}

package lk.ac.mrt.swiftdrop.pricing_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PricingAuthorizationTest {
    @Autowired MockMvc mvc;
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static JwtAuthenticationToken user(String role) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "RS256").subject(UUID.randomUUID().toString())
            .claim("roles", java.util.List.of(role)).build();
        return new JwtAuthenticationToken(jwt, java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    void anonymousZoneReadAndEstimateAreRejected() throws Exception {
        mvc.perform(get("/api/v1/pricing/zones")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/pricing/estimate")).andExpect(status().isUnauthorized());
    }

    @Test
    void customerCanReadActiveZonesButCannotManageOrReadInactiveData() throws Exception {
        mvc.perform(get("/api/v1/pricing/zones").with(SecurityMockMvcRequestPostProcessors.authentication(user("CUSTOMER"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/pricing/zones").param("includeInactive", "true")
            .with(SecurityMockMvcRequestPostProcessors.authentication(user("CUSTOMER")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pricing/zones")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Kandy\",\"code\":\"KAN\",\"cities\":[\"Kandy\"],\"active\":true}")
            .with(SecurityMockMvcRequestPostProcessors.authentication(user("CUSTOMER"))))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/pricing/rules").with(SecurityMockMvcRequestPostProcessors.authentication(user("CUSTOMER"))))
            .andExpect(status().isOk());
    }

    @Test
    void adminCanReadInactiveZonesAndRules() throws Exception {
        mvc.perform(get("/api/v1/pricing/zones").param("includeInactive", "true")
            .with(SecurityMockMvcRequestPostProcessors.authentication(user("ADMIN")))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/pricing/rules").param("includeInactive", "true")
            .with(SecurityMockMvcRequestPostProcessors.authentication(user("ADMIN")))).andExpect(status().isOk());
    }
}
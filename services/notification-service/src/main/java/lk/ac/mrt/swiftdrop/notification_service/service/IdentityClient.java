package lk.ac.mrt.swiftdrop.notification_service.service;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class IdentityClient {

    private final RestClient restClient;

    public IdentityClient(@Value("${app.services.identity-url:http://identity-service:8080}") String identityUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(identityUrl)
                .build();
    }

    public record UserLookupDto(UUID id, String email, String phone, String name) {}

    /**
     * Resolves user details via identity service: /api/v1/internal/users/lookup?id={userId}
     * Falls back gracefully if identity service is not reachable (e.g. offline dev/testing).
     */
    public UserLookupDto lookupUser(UUID userId) {
        if (userId == null) {
            return new UserLookupDto(UUID.randomUUID(), "customer@example.com", "+94771234567", "Valued Customer");
        }

        try {
            return restClient.get()
                    .uri("/api/v1/internal/users/lookup?id={id}", userId)
                    .retrieve()
                    .body(UserLookupDto.class);
        } catch (Exception e) {
            log.warn("Identity lookup failed for userId={}, using resilient fallback: {}", userId, e.getMessage());
            String emailPrefix = userId.toString().substring(0, 8);
            return new UserLookupDto(userId, "user-" + emailPrefix + "@example.com", "+94771234567", "Customer");
        }
    }
}
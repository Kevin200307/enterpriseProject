package lk.ac.mrt.swiftdrop.dispatch_service.client;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchException;

@Component
public class IdentityClient {
    private final RestClient client;
    private final ServiceTokenProvider tokens;
    public IdentityClient(@Value("${dispatch.identity-url}") String url, ServiceTokenProvider tokens) {
        client = HttpClients.create(url); this.tokens = tokens;
    }
    public record User(UUID id, String fullName, Set<String> roles, String status) {}
    public User courier(UUID userId) {
        try {
            var user = client.get().uri("/api/v1/internal/users/{id}",userId)
                    .headers(h -> h.setBearerAuth(tokens.token())).retrieve().body(User.class);
            if (user == null || !userId.equals(user.id()) || user.fullName() == null || user.fullName().isBlank()
                    || user.roles() == null || !user.roles().contains("COURIER") || !"ACTIVE".equals(user.status()))
                throw new DispatchException(400,"The identity user must be active and have the COURIER role");
            return user;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new DispatchException(400,"The identity user does not exist");
            throw new DispatchException(503,"Identity lookup is unavailable");
        } catch (ResourceAccessException ex) { throw new DispatchException(503,"Identity lookup is unavailable"); }
    }
}

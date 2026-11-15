package lk.ac.mrt.swiftdrop.dispatch_service.client;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchException;

@Component
public class ServiceTokenProvider {
    private final RestClient client;
    private final String clientId;
    private final String secret;
    private String cached;
    private Instant expires = Instant.EPOCH;
    public ServiceTokenProvider(@Value("${dispatch.identity-url}") String url,
            @Value("${dispatch.client-id}") String clientId, @Value("${dispatch.client-secret}") String secret) {
        this.client = HttpClients.create(url); this.clientId = clientId; this.secret = secret;
    }
    public record Token(String access_token, long expires_in) {}
    public synchronized String token() {
        if (cached != null && Instant.now().isBefore(expires)) return cached;
        if (secret.isBlank()) throw new DispatchException(503,"Dispatch service credentials are not configured");
        var form = new LinkedMultiValueMap<String,String>(); form.add("grant_type","client_credentials");
        try {
            var token = client.post().uri("/api/v1/auth/service-token").headers(h -> h.setBasicAuth(clientId,secret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Token.class);
            if (token == null || token.access_token() == null || token.expires_in() <= 30) throw new IllegalStateException("Invalid token response");
            cached = token.access_token(); expires = Instant.now().plusSeconds(token.expires_in()-30); return cached;
        } catch (RestClientException | IllegalStateException ex) { throw new DispatchException(503,"Service authentication is unavailable"); }
    }
}

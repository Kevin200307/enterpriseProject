package lk.ac.mrt.swiftdrop.merchant_service.service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * Obtains and caches a short-lived service JWT from the identity service.
 * Used by {@link MerchantOrderService} when an order is created via API key
 * (no user JWT is available to forward to parcel-service).
 */
@Component
public class ServiceTokenProvider {

    private final RestClient client;
    private final String clientSecret;
    private volatile String cached;
    private volatile Instant expires = Instant.EPOCH;

    public ServiceTokenProvider(
            @Value("${identity-service.url:http://identity-service:8080}") String identityUrl,
            @Value("${MERCHANT_CLIENT_SECRET:}") String clientSecret) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(identityUrl).requestFactory(factory).build();
        this.clientSecret = clientSecret;
    }

    private record TokenResponse(String access_token, long expires_in) {}

    public synchronized String token() {
        if (cached != null && Instant.now().isBefore(expires)) return cached;
        if (clientSecret == null || clientSecret.length() < 32) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Merchant service credentials are not configured");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        try {
            var response = client.post()
                    .uri("/api/v1/auth/service-token")
                    .headers(h -> h.setBasicAuth("merchant-service", clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.access_token() == null || response.expires_in() <= 30)
                throw new IllegalStateException("Invalid token response");
            cached = response.access_token();
            expires = Instant.now().plusSeconds(response.expires_in() - 30);
            return cached;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Could not obtain service token from identity service", ex);
        }
    }
}

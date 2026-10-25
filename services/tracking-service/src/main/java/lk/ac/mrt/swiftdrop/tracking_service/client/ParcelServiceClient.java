package lk.ac.mrt.swiftdrop.tracking_service.client;

import java.time.Duration;
import java.util.Optional;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Service-to-service client for authenticated parcel snapshot backfill and private parcel details.
 */
@Slf4j
@Component
public class ParcelServiceClient {

    private final RestClient restClient;

    public ParcelServiceClient(TrackingProperties properties, RestClient.Builder builder) {
        TrackingProperties.ParcelServiceProperties p = properties.parcelService();
        String baseUrl = p != null && p.url() != null ? p.url() : "http://parcel-service:8080";
        Duration connectTimeout = p != null && p.connectTimeout() != null ? p.connectTimeout() : Duration.ofSeconds(2);
        Duration readTimeout = p != null && p.readTimeout() != null ? p.readTimeout() : Duration.ofSeconds(3);
        String token = p != null ? p.token() : null;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) connectTimeout.toMillis());
        requestFactory.setReadTimeout((int) readTimeout.toMillis());

        RestClient.Builder clientBuilder = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (token != null && !token.isBlank()) {
            clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        this.restClient = clientBuilder.build();
    }

    public Optional<ParcelTrackingSnapshotV1> fetchSnapshot(String trackingNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/parcels/by-tracking/{trackingNumber}", trackingNumber)
                    .exchange((req, resp) -> {
                        if (resp.getStatusCode().is2xxSuccessful()) {
                            return Optional.ofNullable(resp.bodyTo(ParcelTrackingSnapshotV1.class));
                        }
                        if (resp.getStatusCode().value() == 404) {
                            return Optional.empty();
                        }
                        log.warn("Upstream parcel-service returned status {} for tracking number {}",
                                resp.getStatusCode(), trackingNumber);
                        return Optional.empty();
                    });
        } catch (Exception ex) {
            log.warn("Failed to fetch parcel snapshot for {} from parcel-service: {}", trackingNumber, ex.getMessage());
            return Optional.empty();
        }
    }

    public Optional<ParcelDetails> fetchParcelDetails(String trackingNumber) {
        try {
            return restClient.get()
                    .uri("/api/v1/parcels/by-tracking/{trackingNumber}/details", trackingNumber)
                    .exchange((req, resp) -> {
                        if (resp.getStatusCode().is2xxSuccessful()) {
                            return Optional.ofNullable(resp.bodyTo(ParcelDetails.class));
                        }
                        if (resp.getStatusCode().value() == 404) {
                            return Optional.empty();
                        }
                        log.warn("Upstream parcel-service returned status {} for parcel details {}",
                                resp.getStatusCode(), trackingNumber);
                        return Optional.empty();
                    });
        } catch (Exception ex) {
            log.warn("Failed to fetch parcel details for {} from parcel-service: {}", trackingNumber, ex.getMessage());
            return Optional.empty();
        }
    }
}

package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.*;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelDtos.BookingRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class HttpPricingClient implements PricingClient {
    private final RestClient client;
    private final String path;
    @Autowired
    public HttpPricingClient(@Value("${parcel.pricing.base-url}") String baseUrl,
                             @Value("${parcel.pricing.estimate-path}") String path) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.path = path;
    }
    HttpPricingClient(RestClient client, String path) {
        this.client = client;
        this.path = path;
    }
    record EstimateRequest(String zoneFrom, String zoneTo, PackageSize size, java.math.BigDecimal weightKg) {}
    @Override
    public Quote estimate(BookingRequest request, String bearerToken) {
        try {
            Quote quote = client.post().uri(path)
                .headers(h -> { if (bearerToken != null) h.setBearerAuth(bearerToken); })
                .body(new EstimateRequest(request.pickup().zone(), request.dropoff().zone(), request.size(), request.weightKg()))
                .retrieve().body(Quote.class);
            if (quote == null || quote.fee() == null || quote.fee().signum() < 0
                || quote.fee().precision() - quote.fee().scale() > 10
                || quote.currency() == null || !quote.currency().matches("[A-Z]{3}")) {
                throw new IllegalStateException("Invalid pricing response");
            }
            return new Quote(quote.fee().setScale(2, RoundingMode.UNNECESSARY), quote.currency());
        } catch (RuntimeException ex) {
            throw new ParcelException(ErrorCode.UPSTREAM_UNAVAILABLE, "Delivery pricing is temporarily unavailable");
        }
    }
}

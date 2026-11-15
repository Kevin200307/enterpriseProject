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
    record EstimateRequest(String fromCity, String toCity, PackageSize size,
                           java.math.BigDecimal weightKg, boolean express) {}
    record EstimateResponse(java.math.BigDecimal total, String currency) {}
    @Override
    public Quote estimate(BookingRequest request, String bearerToken) {
        try {
            EstimateResponse estimate = client.post().uri(path)
                .headers(h -> { if (bearerToken != null) h.setBearerAuth(bearerToken); })
                .body(new EstimateRequest(request.pickup().city(), request.dropoff().city(), request.size(),
                    request.weightKg(), Boolean.TRUE.equals(request.express())))
                .retrieve().body(EstimateResponse.class);
            if (estimate == null || estimate.total() == null || estimate.total().signum() < 0
                || estimate.total().precision() - estimate.total().scale() > 10
                || estimate.currency() == null || !estimate.currency().matches("[A-Z]{3}")) {
                throw new IllegalStateException("Invalid pricing response");
            }
            return new Quote(estimate.total().setScale(2, RoundingMode.UNNECESSARY), estimate.currency());
        } catch (RuntimeException ex) {
            throw new ParcelException(ErrorCode.UPSTREAM_UNAVAILABLE, "Delivery pricing is temporarily unavailable");
        }
    }
}

package lk.ac.mrt.swiftdrop.tracking_service.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tracking")
public record TrackingProperties(
        ParcelServiceProperties parcelService,
        CorsProperties cors,
        CacheProperties cache) {

    public record ParcelServiceProperties(
            String url,
            String token,
            Duration connectTimeout,
            Duration readTimeout) {
        public ParcelServiceProperties {
            if (url == null || url.isBlank()) {
                url = "http://parcel-service:8080";
            }
            if (connectTimeout == null) {
                connectTimeout = Duration.ofSeconds(2);
            }
            if (readTimeout == null) {
                readTimeout = Duration.ofSeconds(3);
            }
        }
    }

    public record CorsProperties(List<String> allowedOrigins) {
        public CorsProperties {
            if (allowedOrigins == null || allowedOrigins.isEmpty()) {
                allowedOrigins = List.of("http://localhost:5173", "http://localhost:5174");
            }
        }
    }

    public record CacheProperties(
            int parcelTtlDays,
            int timelineTtlDays,
            int eventDedupTtlHours) {
        public CacheProperties {
            if (parcelTtlDays <= 0) parcelTtlDays = 90;
            if (timelineTtlDays <= 0) timelineTtlDays = 90;
            if (eventDedupTtlHours <= 0) eventDedupTtlHours = 24;
        }
    }
}

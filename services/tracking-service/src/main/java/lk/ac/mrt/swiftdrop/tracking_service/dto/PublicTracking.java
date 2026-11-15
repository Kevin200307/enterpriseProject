package lk.ac.mrt.swiftdrop.tracking_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

/**
 * Public tracking response DTO matching frontend PublicTracking contract exactly:
 * {@code { trackingNumber, status, originCity, destinationCity, courierFirstName?, eta?, updatedAt, timeline }}.
 * Contains zero PII (no street, no phone numbers, no internal UUIDs).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicTracking(
        String trackingNumber,
        ParcelStatus status,
        String originCity,
        String destinationCity,
        String courierFirstName,
        String eta,
        Instant updatedAt,
        List<TimelineEntry> timeline) {

    public PublicTracking {
        Objects.requireNonNull(trackingNumber, "trackingNumber is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(originCity, "originCity is required");
        Objects.requireNonNull(destinationCity, "destinationCity is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        timeline = timeline != null ? Collections.unmodifiableList(timeline) : List.of();
    }
}

package lk.ac.mrt.swiftdrop.tracking_service.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;

/**
 * Domain projection record representing parcel tracking state.
 * Fully decoupled from Redis storage representations.
 */
public record TrackingProjection(
        String trackingNumber,
        ParcelStatus status,
        String originCity,
        String destinationCity,
        String courierFirstName,
        String eta,
        Instant updatedAt,
        List<TimelineEntry> timeline) {

    public TrackingProjection {
        Objects.requireNonNull(trackingNumber, "trackingNumber is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(originCity, "originCity is required");
        Objects.requireNonNull(destinationCity, "destinationCity is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        timeline = timeline != null ? Collections.unmodifiableList(timeline) : List.of();
    }

    public PublicTracking toPublicDto() {
        return new PublicTracking(
                trackingNumber,
                status,
                originCity,
                destinationCity,
                courierFirstName,
                eta,
                updatedAt,
                timeline
        );
    }
}

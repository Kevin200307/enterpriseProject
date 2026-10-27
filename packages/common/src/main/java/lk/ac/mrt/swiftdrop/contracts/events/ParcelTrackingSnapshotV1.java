package lk.ac.mrt.swiftdrop.contracts.events;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;

/**
 * Privacy-safe snapshot contract for parcel tracking.
 * <p>
 * <b>Producer ownership:</b> {@code parcel-service} produces this snapshot either as an
 * internal event or via authenticated backfill endpoint {@code GET /api/v1/parcels/by-tracking/{tn}}.
 * <p>
 * <b>Consumer ownership:</b> {@code tracking-service} consumes this contract to build and
 * maintain the Redis tracking projection and public tracking views.
 * <p>
 * <b>Privacy guarantee:</b> Contains only high-level cities, courier display first name, ETA,
 * status timeline and note. NEVER includes customer/recipient names, phone numbers, full street
 * addresses, merchant IDs, or actor IDs.
 */
public record ParcelTrackingSnapshotV1(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        String originCity,
        String destinationCity,
        String courierFirstName,
        String eta,
        Instant updatedAt,
        List<SnapshotTimelineEntryV1> timeline) {

    public static final String EVENT_TYPE = "parcel.tracking.snapshot";
    public static final int VERSION = 1;

    public record SnapshotTimelineEntryV1(
            ParcelStatus status,
            Instant occurredAt,
            String note) {

        public SnapshotTimelineEntryV1 {
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(occurredAt, "occurredAt is required");
        }
    }

    public ParcelTrackingSnapshotV1 {
        Objects.requireNonNull(trackingNumber, "trackingNumber is required");
        TrackingNumber.validate(trackingNumber);
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(originCity, "originCity is required");
        Objects.requireNonNull(destinationCity, "destinationCity is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
        timeline = timeline != null ? List.copyOf(timeline) : List.of();
    }

    public EventEnvelope<ParcelTrackingSnapshotV1> toEnvelope(String producer) {
        return EventEnvelope.of(EVENT_TYPE, VERSION, producer, this);
    }
}

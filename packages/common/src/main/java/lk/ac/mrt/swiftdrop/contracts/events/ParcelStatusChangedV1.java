package lk.ac.mrt.swiftdrop.contracts.events;

import java.util.Objects;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

/**
 * Published by parcel-service whenever a parcel changes status.
 * {@code fromStatus} is null for a newly created parcel; {@code merchantId} is null for
 * customer bookings; {@code actorId} is null for system-initiated changes.
 * Breaking changes must go into a new {@code ParcelStatusChangedV2}, never edit this record's fields.
 */
public record ParcelStatusChangedV1(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus fromStatus,
        ParcelStatus toStatus,
        UUID actorId,
        UUID merchantId) {

    public static final String EVENT_TYPE = "parcel.status.changed";
    public static final int VERSION = 1;

    public ParcelStatusChangedV1 {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(toStatus, "toStatus");
    }

    public EventEnvelope<ParcelStatusChangedV1> toEnvelope(String producer) {
        return EventEnvelope.of(EVENT_TYPE, VERSION, producer, this);
    }
}

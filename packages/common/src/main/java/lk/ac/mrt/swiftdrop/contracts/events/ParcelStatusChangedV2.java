package lk.ac.mrt.swiftdrop.contracts.events;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

import java.util.Objects;
import java.util.UUID;

/** Private status event with explicit owners, assignee, actor, failure, and proof context. */
public record ParcelStatusChangedV2(
        UUID parcelId,
        String trackingNumber,
        UUID customerUserId,
        UUID merchantId,
        UUID assigneeUserId,
        ParcelStatus fromStatus,
        ParcelStatus toStatus,
        UUID changedByUserId,
        String failureReason,
        UUID proofId) {

    public static final String EVENT_TYPE = ParcelStatusChangedV1.EVENT_TYPE;
    public static final int VERSION = 2;

    public ParcelStatusChangedV2 {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(customerUserId, "customerUserId");
        Objects.requireNonNull(toStatus, "toStatus");
    }

    public EventEnvelope<ParcelStatusChangedV2> toEnvelope(String producer) {
        return EventEnvelope.of(EVENT_TYPE, VERSION, producer, this);
    }
}

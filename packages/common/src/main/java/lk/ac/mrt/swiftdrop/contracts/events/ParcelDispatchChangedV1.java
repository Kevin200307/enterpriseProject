package lk.ac.mrt.swiftdrop.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

/** Full dispatch snapshot, emitted on creation, assignment and status changes.
 * parcelVersion is the committed aggregate version, used to discard out-of-order snapshots.
 * assigneeUserId is an identity user UUID, never a dispatch courier profile UUID.
 * This event contains private addresses: bind only trusted internal consumers.
 */
public record ParcelDispatchChangedV1(UUID parcelId, String trackingNumber, long parcelVersion,
        ParcelStatus status, UUID assigneeUserId, Address pickup, Address dropoff,
        PackageSize size, BigDecimal weightKg, Instant createdAt, String note) {
    public static final String EVENT_TYPE = "parcel.dispatch.changed";
    public static final int VERSION = 1;

    public record Address(String contactName, String phone, String line1, String line2,
                          String city, String postalCode, String zoneCode) {
        public Address {
            Objects.requireNonNull(contactName, "contactName");
            Objects.requireNonNull(phone, "phone");
            Objects.requireNonNull(line1, "line1");
            Objects.requireNonNull(city, "city");
            Objects.requireNonNull(postalCode, "postalCode");
            Objects.requireNonNull(zoneCode, "zoneCode");
        }
    }

    public ParcelDispatchChangedV1 {
        Objects.requireNonNull(parcelId, "parcelId");
        Objects.requireNonNull(trackingNumber, "trackingNumber");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(pickup, "pickup");
        Objects.requireNonNull(dropoff, "dropoff");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(weightKg, "weightKg");
        Objects.requireNonNull(createdAt, "createdAt");
        if (parcelVersion < 0 || weightKg.signum() <= 0) throw new IllegalArgumentException("Invalid version or weight");
    }

    public EventEnvelope<ParcelDispatchChangedV1> toEnvelope(String producer) {
        return EventEnvelope.of(EVENT_TYPE, VERSION, producer, this);
    }
}

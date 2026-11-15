package lk.ac.mrt.swiftdrop.dispatch_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.*;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelDispatchChangedV1;
import lombok.*;

@Entity @Table(name="dispatch_parcels") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class DispatchParcel {
    @Id private UUID id;
    @Version private long version;
    private long parcelVersion;
    private String trackingNumber;
    @Enumerated(EnumType.STRING) private ParcelStatus status;
    private UUID assigneeUserId;
    private String pickupCity;
    private String dropoffCity;
    private String zoneCode;
    @Enumerated(EnumType.STRING) private PackageSize size;
    @Column(precision=10,scale=3) private BigDecimal weightKg;
    @Column(columnDefinition="text") private String snapshot;
    private Instant createdAt;
    private Instant statusAt;

    public void apply(ParcelDispatchChangedV1 event, String json, Instant occurredAt) {
        parcelVersion = event.parcelVersion(); trackingNumber = event.trackingNumber(); status = event.status();
        assigneeUserId = event.assigneeUserId(); pickupCity = event.pickup().city(); dropoffCity = event.dropoff().city();
        zoneCode = event.pickup().zoneCode(); size = event.size(); weightKg = event.weightKg();
        snapshot = json; createdAt = event.createdAt(); statusAt = occurredAt;
    }
    public boolean terminal() { return status == ParcelStatus.DELIVERED || status == ParcelStatus.FAILED; }
    public boolean assignable() { return status == ParcelStatus.CREATED || status == ParcelStatus.FAILED; }
    public void assigned(UUID userId, long remoteVersion) {
        if (remoteVersion >= parcelVersion) { assigneeUserId = userId; parcelVersion = remoteVersion; }
    }
}

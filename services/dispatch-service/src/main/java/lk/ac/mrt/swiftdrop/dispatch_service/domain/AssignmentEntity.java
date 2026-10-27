package lk.ac.mrt.swiftdrop.dispatch_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.*;

@Entity @Table(name="assignments") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class AssignmentEntity {
    @Id private UUID id;
    @Version private long version;
    private UUID parcelId;
    private UUID activeParcelId;
    private UUID courierId;
    private UUID assignedBy;
    private Instant assignedAt;
    private Instant closedAt;
    private Instant completedAt;
    private Instant failedAt;
    private String closeReason;
    @Enumerated(EnumType.STRING) private ParcelStatus status;

    public AssignmentEntity(UUID parcel, UUID courier, UUID actor, Instant now, ParcelStatus status) {
        id = UUID.randomUUID(); parcelId = parcel; activeParcelId = parcel; courierId = courier;
        assignedBy = actor; assignedAt = now; this.status = status;
    }
    public void close(Instant now, String reason) { closedAt = now; closeReason = reason; activeParcelId = null; }
    public void mirror(ParcelStatus status, Instant now) {
        this.status = status;
        if (status == ParcelStatus.DELIVERED) { completedAt = now; close(now,"Delivered"); }
        if (status == ParcelStatus.FAILED) { failedAt = now; close(now,"Failed"); }
    }
}

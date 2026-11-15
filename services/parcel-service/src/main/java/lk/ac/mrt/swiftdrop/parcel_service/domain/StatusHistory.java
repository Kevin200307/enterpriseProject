package lk.ac.mrt.swiftdrop.parcel_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

@Entity
@Table(name="parcel_status_history")
public class StatusHistory {
    @Id private UUID id;
    private UUID parcelId;
    @Enumerated(EnumType.STRING) private ParcelStatus fromStatus;
    @Enumerated(EnumType.STRING) private ParcelStatus toStatus;
    private UUID actorId;
    private String note;
    private UUID proofId;
    private String failedReason;
    private Instant occurredAt;

    public StatusHistory(UUID parcelId, ParcelStatus from, ParcelStatus to, UUID actorId,
                         String note, UUID proofId, String failedReason, Instant now) {
        this.id = UUID.randomUUID();
        this.parcelId = parcelId;
        this.fromStatus = from;
        this.toStatus = to;
        this.actorId = actorId;
        this.note = note;
        this.proofId = proofId;
        this.failedReason = failedReason;
        this.occurredAt = now;
    }

    public UUID getId() { return id; }

    public UUID getParcelId() { return parcelId; }

    public ParcelStatus getFromStatus() { return fromStatus; }

    public ParcelStatus getToStatus() { return toStatus; }

    public UUID getActorId() { return actorId; }

    public String getNote() { return note; }

    public UUID getProofId() { return proofId; }

    public String getFailedReason() { return failedReason; }

    public Instant getOccurredAt() { return occurredAt; }

    protected StatusHistory() {}
}

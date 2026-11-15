package lk.ac.mrt.swiftdrop.parcel_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="delivery_proofs")
public class DeliveryProof {
    @Id private UUID id;
    private UUID parcelId;
    private UUID uploadedBy;
    private String contentType;
    private long sizeBytes;
    private Instant createdAt;
    private String objectKey;

    public UUID getId() { return id; }

    public UUID getParcelId() { return parcelId; }

    public UUID getUploadedBy() { return uploadedBy; }

    public String getContentType() { return contentType; }

    public long getSizeBytes() { return sizeBytes; }

    public Instant getCreatedAt() { return createdAt; }

    public String getObjectKey() { return objectKey; }

    protected DeliveryProof() {}

    public DeliveryProof(UUID id, UUID parcelId, UUID uploadedBy, String contentType, long sizeBytes, Instant createdAt, String objectKey) {
        this.id = id;
        this.parcelId = parcelId;
        this.uploadedBy = uploadedBy;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = createdAt;
        this.objectKey = objectKey;
    }
}

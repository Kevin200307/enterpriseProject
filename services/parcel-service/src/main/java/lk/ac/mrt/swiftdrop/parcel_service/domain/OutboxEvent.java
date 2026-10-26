package lk.ac.mrt.swiftdrop.parcel_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="parcel_outbox")
public class OutboxEvent {
    @Id private UUID id;
    @Column(columnDefinition="text") private String payload;
    private Instant createdAt;
    private Instant publishedAt;
    public OutboxEvent(UUID id, String payload, Instant now) {
        this.id = id;
        this.payload = payload;
        this.createdAt = now;
    }
    public void published(Instant now) { this.publishedAt = now; }

    public UUID getId() { return id; }

    public String getPayload() { return payload; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getPublishedAt() { return publishedAt; }

    protected OutboxEvent() {}
}

package lk.ac.mrt.swiftdrop.notification_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_event_channel_recipient",
            columnNames = {"event_id", "channel", "recipient_ref"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "parcel_id", nullable = false)
    private UUID parcelId;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(name = "recipient_ref", nullable = false)
    private UUID recipientRef; // User ID / Actor ID

    @Column(name = "recipient", nullable = false)
    private String recipient; // Resolved email or masked phone

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NotificationStatus status;

    @Column(name = "template", nullable = false, length = 64)
    private String template;

    @Column(name = "subject")
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0; // Number of delivery attempts

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "sent_at")
    private Instant sentAt; // Timestamp when sent successfully

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }
}
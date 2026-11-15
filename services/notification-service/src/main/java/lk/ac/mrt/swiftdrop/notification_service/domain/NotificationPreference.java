package lk.ac.mrt.swiftdrop.notification_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity tracking user notification preferences per channel and parcel status.
 */
@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Builder.Default
    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Builder.Default
    @Column(name = "email_statuses", nullable = false)
    private String emailStatuses = "CREATED,PICKED_UP,IN_TRANSIT,OUT_FOR_DELIVERY,DELIVERED,FAILED,CANCELLED";

    @Builder.Default
    @Column(name = "sms_enabled", nullable = false)
    private boolean smsEnabled = true;

    /**
     * Comma-separated parcel statuses enabled for SMS.
     * Default per plan.md: OUT_FOR_DELIVERY,DELIVERED
     */
    @Builder.Default
    @Column(name = "sms_statuses", nullable = false)
    private String smsStatuses = "OUT_FOR_DELIVERY,DELIVERED";

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Set<ParcelStatus> getParsedSmsStatuses() {
        return parseStatuses(smsStatuses);
    }

    public void setParsedSmsStatuses(Set<ParcelStatus> statuses) {
        this.smsStatuses = serializeStatuses(statuses);
    }

    public Set<ParcelStatus> getParsedEmailStatuses() {
        return parseStatuses(emailStatuses);
    }

    public void setParsedEmailStatuses(Set<ParcelStatus> statuses) {
        this.emailStatuses = serializeStatuses(statuses);
    }

    private static Set<ParcelStatus> parseStatuses(String statuses) {
        if (statuses == null || statuses.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(statuses.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ParcelStatus::valueOf)
                .collect(Collectors.toSet());
    }

    private static String serializeStatuses(Set<ParcelStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return "";
        }
        return statuses.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public static NotificationPreference defaultFor(UUID userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .emailStatuses("CREATED,PICKED_UP,IN_TRANSIT,OUT_FOR_DELIVERY,DELIVERED,FAILED,CANCELLED")
                .smsEnabled(true)
                .smsStatuses("OUT_FOR_DELIVERY,DELIVERED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

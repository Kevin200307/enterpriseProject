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
        if (smsStatuses == null || smsStatuses.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(smsStatuses.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(ParcelStatus::valueOf)
                .collect(Collectors.toSet());
    }

    public void setParsedSmsStatuses(Set<ParcelStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            this.smsStatuses = "";
        } else {
            this.smsStatuses = statuses.stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
        }
    }

    public static NotificationPreference defaultFor(UUID userId) {
        return NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .smsEnabled(true)
                .smsStatuses("OUT_FOR_DELIVERY,DELIVERED")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }
}

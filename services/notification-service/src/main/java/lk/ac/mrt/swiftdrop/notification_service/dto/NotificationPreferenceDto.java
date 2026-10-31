package lk.ac.mrt.swiftdrop.notification_service.dto;

import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;

public record NotificationPreferenceDto(
        @NotNull UUID userId,
        @JsonProperty("EMAIL") Set<ParcelStatus> emailStatuses,
        @JsonProperty("SMS") Set<ParcelStatus> smsStatuses
) {
    public static NotificationPreferenceDto fromEntity(NotificationPreference entity) {
        return new NotificationPreferenceDto(
                entity.getUserId(),
                entity.isEmailEnabled() ? entity.getParsedEmailStatuses() : Set.of(),
                entity.isSmsEnabled() ? entity.getParsedSmsStatuses() : Set.of()
        );
    }
}

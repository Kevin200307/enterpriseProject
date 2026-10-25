package lk.ac.mrt.swiftdrop.notification_service.dto;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;

public record NotificationPreferenceDto(
        @NotNull UUID userId,
        boolean emailEnabled,
        boolean smsEnabled,
        Set<ParcelStatus> smsStatuses
) {
    public static NotificationPreferenceDto fromEntity(NotificationPreference entity) {
        return new NotificationPreferenceDto(
                entity.getUserId(),
                entity.isEmailEnabled(),
                entity.isSmsEnabled(),
                entity.getParsedSmsStatuses()
        );
    }
}

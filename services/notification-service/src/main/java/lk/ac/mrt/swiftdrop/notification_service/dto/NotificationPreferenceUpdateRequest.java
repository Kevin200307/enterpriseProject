package lk.ac.mrt.swiftdrop.notification_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

public record NotificationPreferenceUpdateRequest(
        @NotNull @JsonProperty("EMAIL") Set<ParcelStatus> emailStatuses,
        @NotNull @JsonProperty("SMS") Set<ParcelStatus> smsStatuses
) {
    public NotificationPreferenceDto forUser(java.util.UUID userId) {
        return new NotificationPreferenceDto(userId, emailStatuses, smsStatuses);
    }
}

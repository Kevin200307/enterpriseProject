package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;

/**
 * Request payload to update an existing user's account status.
 */
@Schema(description = "Request payload to update a user's account status")
public record UpdateUserStatusRequest(
        @NotNull(message = "status is required")
        @Schema(description = "New account status for the user", example = "SUSPENDED")
        UserStatus status
) {
}

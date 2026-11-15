package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;

import java.util.Set;

/**
 * Request payload to update an existing user's assigned system roles.
 */
@Schema(description = "Request payload to update a user's assigned roles")
public record UpdateUserRolesRequest(
        @NotEmpty(message = "roles must not be empty")
        @Schema(description = "Set of roles to assign to the user", example = "[\"ADMIN\", \"CUSTOMER\"]")
        Set<Role> roles
) {
}

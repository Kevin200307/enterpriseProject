package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DTO representing user details for internal inter-service lookups.
 * Strictly avoids exposing password hashes or internal secrets.
 * Provides 'fullName' and 'name' aliases for broad compatibility across services.
 */
@Schema(description = "User lookup response for inter-service communication")
public record UserLookupResponse(
        UUID id,
        String email,
        String fullName,
        String name,
        String phone,
        UserStatus status,
        Set<String> roles
) {
    public static UserLookupResponse fromEntity(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(RoleEntity::getId)
                .collect(Collectors.toSet());

        return new UserLookupResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getFullName(), // name alias for notification-service UserLookupDto
                user.getPhone(),
                user.getStatus(),
                roleNames
        );
    }
}

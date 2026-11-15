package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Public representation of a User.
 * Strictly guarantees password hashes are never exposed in any API response.
 */
public record UserResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        UserStatus status,
        Set<Role> roles,
        boolean mustChangePassword,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse fromEntity(User user) {
        Set<Role> roles = user.getRoles().stream()
                .map(RoleEntity::toRole)
                .collect(Collectors.toSet());

        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getStatus(),
                roles,
                user.isMustChangePassword(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}

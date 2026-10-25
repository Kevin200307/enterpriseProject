package lk.ac.mrt.swiftdrop.identity_service.domain;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("Should initialize user with ACTIVE status and lowercase email")
    void shouldInitializeUserCorrectly() {
        User user = new User("  Amandi.Arangala@SwiftDrop.LK  ", "hashedPass", "Amandi Arangala", "+94771234567");

        assertThat(user.getEmail()).isEqualTo("amandi.arangala@swiftdrop.lk");
        assertThat(user.getFullName()).isEqualTo("Amandi Arangala");
        assertThat(user.getPhone()).isEqualTo("+94771234567");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.isSuspended()).isFalse();
        assertThat(user.isMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("Should handle role addition, check, and removal")
    void shouldManageRoles() {
        User user = new User("user@swiftdrop.lk", "hash", "Test User", "+94770000000");
        RoleEntity customerRole = new RoleEntity(Role.CUSTOMER, "Customer role");
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "Admin role");

        user.addRole(customerRole);

        assertThat(user.hasRole(Role.CUSTOMER)).isTrue();
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
        assertThat(customerRole.toRole()).isEqualTo(Role.CUSTOMER);

        user.addRole(adminRole);
        assertThat(user.hasRole(Role.ADMIN)).isTrue();

        user.removeRole(adminRole);
        assertThat(user.hasRole(Role.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("Should handle suspend and reactivate state transitions")
    void shouldTransitionStatus() {
        User user = new User("user@swiftdrop.lk", "hash", "Test User", "+94770000000");

        user.suspend();
        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        assertThat(user.isSuspended()).isTrue();
        assertThat(user.isActive()).isFalse();

        user.reactivate();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.isSuspended()).isFalse();
    }
}

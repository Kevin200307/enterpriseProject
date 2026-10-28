package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.config.AdminBootstrapProperties;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminBootstrapServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private ApplicationArguments args;

    private AdminBootstrapProperties defaultProperties;
    private AdminBootstrapService bootstrapService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        args = mock(ApplicationArguments.class);

        defaultProperties = new AdminBootstrapProperties(
                true,
                "admin@swiftdrop.lk",
                "Admin123!Safe",
                "SwiftDrop Platform Administrator",
                "+94770000001"
        );

        bootstrapService = new AdminBootstrapService(
                userRepository,
                roleRepository,
                passwordEncoder,
                defaultProperties
        );
    }

    @Test
    @DisplayName("Should provision initial admin user with mustChangePassword=true when not present")
    void shouldProvisionAdminUserWhenNotExists() {
        when(userRepository.findByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(Optional.empty());
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "System Administrator");
        when(roleRepository.findByRole(Role.ADMIN)).thenReturn(Optional.of(adminRole));
        when(passwordEncoder.encode("Admin123!Safe")).thenReturn("$2a$10$mockHashedPassword");

        bootstrapService.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User savedUser = captor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("admin@swiftdrop.lk");
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2a$10$mockHashedPassword");
        assertThat(savedUser.getFullName()).isEqualTo("SwiftDrop Platform Administrator");
        assertThat(savedUser.getPhone()).isEqualTo("+94770000001");
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.isMustChangePassword()).isTrue();
        assertThat(savedUser.hasRole(Role.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("Should create ADMIN role entity if missing when provisioning initial admin")
    void shouldCreateAdminRoleEntityIfMissing() {
        when(userRepository.findByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(Optional.empty());
        when(roleRepository.findByRole(Role.ADMIN)).thenReturn(Optional.empty());
        RoleEntity createdRole = new RoleEntity(Role.ADMIN, "System Administrator");
        when(roleRepository.save(any(RoleEntity.class))).thenReturn(createdRole);
        when(passwordEncoder.encode("Admin123!Safe")).thenReturn("$2a$10$mockHashedPassword");

        bootstrapService.run(args);

        verify(roleRepository).save(any(RoleEntity.class));
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should not modify existing admin user if they already have ADMIN role and ACTIVE status")
    void shouldNotModifyExistingAdminWhenAlreadyConfigured() {
        User existingAdmin = new User("admin@swiftdrop.lk", "hash", "Admin", "+94770000001");
        existingAdmin.setStatus(UserStatus.ACTIVE);
        existingAdmin.addRole(new RoleEntity(Role.ADMIN, "Admin"));

        when(userRepository.findByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(Optional.of(existingAdmin));

        bootstrapService.run(args);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should assign ADMIN role and activate existing user if role is missing or status is suspended")
    void shouldAssignAdminRoleAndActivateExistingUser() {
        User existingUser = new User("admin@swiftdrop.lk", "hash", "Admin", "+94770000001");
        existingUser.setStatus(UserStatus.SUSPENDED);
        existingUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));

        when(userRepository.findByEmailIgnoreCase("admin@swiftdrop.lk")).thenReturn(Optional.of(existingUser));
        when(roleRepository.findByRole(Role.ADMIN)).thenReturn(Optional.of(new RoleEntity(Role.ADMIN, "Admin")));

        bootstrapService.run(args);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User updatedUser = captor.getValue();
        assertThat(updatedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(updatedUser.hasRole(Role.ADMIN)).isTrue();
        assertThat(updatedUser.hasRole(Role.CUSTOMER)).isTrue();
    }

    @Test
    @DisplayName("Should skip bootstrap execution when enabled is false")
    void shouldSkipWhenBootstrapDisabled() {
        AdminBootstrapProperties disabledProperties = new AdminBootstrapProperties(
                false,
                "admin@swiftdrop.lk",
                "Admin123!Safe",
                "SwiftDrop Platform Administrator",
                "+94770000001"
        );
        AdminBootstrapService disabledService = new AdminBootstrapService(
                userRepository,
                roleRepository,
                passwordEncoder,
                disabledProperties
        );

        disabledService.run(args);

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail fast when enabled bootstrap email is blank")
    void shouldFailWhenBootstrapEmailIsBlank() {
        AdminBootstrapProperties blankEmailProperties = mock(AdminBootstrapProperties.class);
        when(blankEmailProperties.enabled()).thenReturn(true);
        when(blankEmailProperties.bootstrapEmail()).thenReturn("   ");

        AdminBootstrapService blankEmailService = new AdminBootstrapService(
                userRepository,
                roleRepository,
                passwordEncoder,
                blankEmailProperties
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> blankEmailService.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_EMAIL");

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should fail fast when enabled bootstrap password is blank")
    void shouldFailWhenBootstrapPasswordIsBlank() {
        AdminBootstrapProperties properties = new AdminBootstrapProperties(
                true, "admin@swiftdrop.lk", " ", "Admin", "+94770000001");
        AdminBootstrapService service = new AdminBootstrapService(
                userRepository, roleRepository, passwordEncoder, properties);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.run(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
        verify(userRepository, never()).save(any());
    }
}

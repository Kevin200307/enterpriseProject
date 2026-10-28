package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.ConflictException;
import lk.ac.mrt.swiftdrop.identity_service.error.DuplicateEmailException;
import lk.ac.mrt.swiftdrop.identity_service.error.ResourceNotFoundException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.RefreshTokenService;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private UserServiceImpl userService;

    private RoleEntity customerRole;

    @BeforeEach
    void setUp() {
        customerRole = new RoleEntity(Role.CUSTOMER, "Customer role");
    }

    @Test
    @DisplayName("Should successfully register a new customer user")
    void shouldRegisterNewUser() {
        RegisterRequest request = new RegisterRequest(
                "Amandi.Arangala@SwiftDrop.lk",
                "SecurePassword123",
                "Amandi Arangala",
                "+94771234567"
        );

        when(userRepository.existsByEmailIgnoreCase("amandi.arangala@swiftdrop.lk")).thenReturn(false);
        when(roleRepository.findByRole(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(passwordEncoder.encode("SecurePassword123")).thenReturn("$2b$10$hashedPasswordSample");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            u.setCreatedAt(Instant.now());
            u.setUpdatedAt(Instant.now());
            return u;
        });

        UserResponse response = userService.register(request);

        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("amandi.arangala@swiftdrop.lk");
        assertThat(response.fullName()).isEqualTo("Amandi Arangala");
        assertThat(response.phone()).isEqualTo("+94771234567");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.roles()).containsExactly(Role.CUSTOMER);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User savedUser = captor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("$2b$10$hashedPasswordSample");
        assertThat(savedUser.hasRole(Role.CUSTOMER)).isTrue();
    }

    @Test
    @DisplayName("Should throw DuplicateEmailException when email is already registered")
    void shouldRejectDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "existing@swiftdrop.lk",
                "Password123",
                "Existing User",
                "+94770000000"
        );

        when(userRepository.existsByEmailIgnoreCase("existing@swiftdrop.lk")).thenReturn(true);

        assertThatThrownBy(() -> userService.register(request))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("A user with this email address already exists");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("Should retrieve user profile when user exists")
    void shouldGetUserProfile() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@swiftdrop.lk", "hash", "Test User", "+94771111111");
        user.setId(userId);
        user.addRole(customerRole);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.getUserProfile(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("user@swiftdrop.lk");
        assertThat(response.fullName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user profile does not exist")
    void shouldThrowWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should update user profile fields")
    void shouldUpdateProfile() {
        UUID userId = UUID.randomUUID();
        User user = new User("user@swiftdrop.lk", "hash", "Old Name", "+94771111111");
        user.setId(userId);
        user.addRole(customerRole);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "+94772222222");
        UserResponse response = userService.updateProfile(userId, request);

        assertThat(response.fullName()).isEqualTo("New Name");
        assertThat(response.phone()).isEqualTo("+94772222222");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should list users without filters by delegating to findAll")
    void shouldListUsersWithoutFilters() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User("user@swiftdrop.lk", "hash", "Test User", "+94771111111");
        user.setId(UUID.randomUUID());
        user.addRole(customerRole);

        when(userRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = userService.listUsers(null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("user@swiftdrop.lk");
        verify(userRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should list users filtered by role normalized to uppercase")
    void shouldListUsersFilteredByRole() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User("customer@swiftdrop.lk", "hash", "Customer User", "+94771111111");
        user.setId(UUID.randomUUID());
        user.addRole(customerRole);

        when(userRepository.findByRoles_Id("CUSTOMER", pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = userService.listUsers("customer", null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("customer@swiftdrop.lk");
        verify(userRepository).findByRoles_Id("CUSTOMER", pageable);
    }

    @Test
    @DisplayName("Should list users filtered by status")
    void shouldListUsersFilteredByStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User("active@swiftdrop.lk", "hash", "Active User", "+94771111111");
        user.setId(UUID.randomUUID());
        user.addRole(customerRole);

        when(userRepository.findByStatus(UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = userService.listUsers(null, UserStatus.ACTIVE, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("active@swiftdrop.lk");
        verify(userRepository).findByStatus(UserStatus.ACTIVE, pageable);
    }

    @Test
    @DisplayName("Should list users filtered by both role and status")
    void shouldListUsersFilteredByRoleAndStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User("admin@swiftdrop.lk", "hash", "Admin User", "+94771111111");
        user.setId(UUID.randomUUID());
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "Admin role");
        user.addRole(adminRole);

        when(userRepository.findByRoles_IdAndStatus("ADMIN", UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = userService.listUsers(" admin ", UserStatus.ACTIVE, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).email()).isEqualTo("admin@swiftdrop.lk");
        verify(userRepository).findByRoles_IdAndStatus("ADMIN", UserStatus.ACTIVE, pageable);
    }

    @Test
    @DisplayName("Should treat empty or whitespace-only role as null filter")
    void shouldTreatBlankRoleAsNull() {
        Pageable pageable = PageRequest.of(0, 10);
        User user = new User("user@swiftdrop.lk", "hash", "User", "+94771111111");
        user.setId(UUID.randomUUID());
        user.addRole(customerRole);

        when(userRepository.findByStatus(UserStatus.ACTIVE, pageable))
                .thenReturn(new PageImpl<>(List.of(user), pageable, 1));

        Page<UserResponse> result = userService.listUsers("   ", UserStatus.ACTIVE, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(userRepository).findByStatus(UserStatus.ACTIVE, pageable);
    }

    @Test
    @DisplayName("Should suspend user and immediately revoke all active refresh tokens")
    void shouldSuspendUserAndRevokeTokens() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = new User("courier@swiftdrop.lk", "hash", "Courier", "+94771111111");
        user.setId(targetUserId);
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(customerRole);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserStatus(targetUserId, UserStatus.SUSPENDED, adminId);

        assertThat(response.status()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
        verify(refreshTokenService).revokeAllForUser(targetUserId);
    }

    @Test
    @DisplayName("Should reactivate suspended user without revoking tokens")
    void shouldReactivateUserWithoutRevokingTokens() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = new User("user@swiftdrop.lk", "hash", "User", "+94771111111");
        user.setId(targetUserId);
        user.setStatus(UserStatus.SUSPENDED);
        user.addRole(customerRole);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserStatus(targetUserId, UserStatus.ACTIVE, adminId);

        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
        verify(userRepository).save(user);
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when admin attempts to suspend their own account")
    void shouldPreventAdminSelfSuspension() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateUserStatus(adminId, UserStatus.SUSPENDED, adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You cannot suspend your own account");

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user does not exist on status update")
    void shouldThrowWhenUserNotFoundOnUpdateStatus() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserStatus(targetUserId, UserStatus.SUSPENDED, adminId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId or status is null")
    void shouldThrowWhenArgumentsAreNull() {
        UUID validId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateUserStatus(null, UserStatus.ACTIVE, validId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> userService.updateUserStatus(validId, null, validId))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserLookupResponse;
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
import java.util.Set;
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

    @Test
    @DisplayName("Should successfully update user roles")
    void shouldUpdateUserRolesSuccessfully() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = new User("courier@swiftdrop.lk", "hash", "Courier", "+94771111111");
        user.setId(targetUserId);
        user.addRole(customerRole);

        RoleEntity courierRole = new RoleEntity(Role.COURIER, "Courier role");

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(roleRepository.findByRole(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(roleRepository.findByRole(Role.COURIER)).thenReturn(Optional.of(courierRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserRoles(
                targetUserId,
                Set.of(Role.CUSTOMER, Role.COURIER),
                adminId
        );

        assertThat(response.roles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.COURIER);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should throw ConflictException when admin attempts to remove their own ADMIN role")
    void shouldPreventAdminFromRemovingOwnAdminRole() {
        UUID adminId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateUserRoles(adminId, Set.of(Role.CUSTOMER), adminId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You cannot remove your own admin access");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should allow admin to update own roles when ADMIN role is retained")
    void shouldAllowAdminToUpdateOwnRolesWhenAdminRetained() {
        UUID adminId = UUID.randomUUID();
        User adminUser = new User("admin@swiftdrop.lk", "hash", "Admin", "+94771111111");
        adminUser.setId(adminId);
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "Admin role");
        RoleEntity dispatcherRole = new RoleEntity(Role.DISPATCHER, "Dispatcher role");
        adminUser.addRole(adminRole);

        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(roleRepository.findByRole(Role.ADMIN)).thenReturn(Optional.of(adminRole));
        when(roleRepository.findByRole(Role.DISPATCHER)).thenReturn(Optional.of(dispatcherRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserRoles(
                adminId,
                Set.of(Role.ADMIN, Role.DISPATCHER),
                adminId
        );

        assertThat(response.roles()).containsExactlyInAnyOrder(Role.ADMIN, Role.DISPATCHER);
        verify(userRepository).save(adminUser);
    }

    @Test
    @DisplayName("Should allow admin to remove ADMIN role from another user")
    void shouldAllowAdminToRemoveAdminRoleFromAnotherUser() {
        UUID otherUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User otherUser = new User("exadmin@swiftdrop.lk", "hash", "Ex Admin", "+94771111111");
        otherUser.setId(otherUserId);
        RoleEntity adminRole = new RoleEntity(Role.ADMIN, "Admin role");
        otherUser.addRole(adminRole);

        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(otherUser));
        when(roleRepository.findByRole(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = userService.updateUserRoles(
                otherUserId,
                Set.of(Role.CUSTOMER),
                adminId
        );

        assertThat(response.roles()).containsExactly(Role.CUSTOMER);
        verify(userRepository).save(otherUser);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user not found on update roles")
    void shouldThrowWhenUserNotFoundOnUpdateRoles() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(userRepository.findById(targetUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRoles(targetUserId, Set.of(Role.CUSTOMER), adminId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when role does not exist in repository")
    void shouldThrowWhenRoleNotFoundInRepository() {
        UUID targetUserId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        User user = new User("user@swiftdrop.lk", "hash", "User", "+94771111111");
        user.setId(targetUserId);

        when(userRepository.findById(targetUserId)).thenReturn(Optional.of(user));
        when(roleRepository.findByRole(Role.DISPATCHER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUserRoles(targetUserId, Set.of(Role.DISPATCHER), adminId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when userId is null or roles set is empty")
    void shouldThrowWhenRolesArgumentsAreInvalid() {
        UUID validId = UUID.randomUUID();

        assertThatThrownBy(() -> userService.updateUserRoles(null, Set.of(Role.CUSTOMER), validId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> userService.updateUserRoles(validId, null, validId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> userService.updateUserRoles(validId, Set.of(), validId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should add MERCHANT role without replacing existing roles")
    void shouldGrantMerchantRoleAndPreserveExistingRoles() {
        UUID userId = UUID.randomUUID();
        User user = new User("merchant@swiftdrop.lk", "hash", "Merchant", "+94770000000");
        user.setId(userId);
        user.addRole(customerRole);
        RoleEntity merchantRole = new RoleEntity(Role.MERCHANT, "Merchant role");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByRole(Role.MERCHANT)).thenReturn(Optional.of(merchantRole));
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = userService.grantMerchantRole(userId);

        assertThat(response.roles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.MERCHANT);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("Should grant MERCHANT role idempotently")
    void shouldGrantMerchantRoleIdempotently() {
        UUID userId = UUID.randomUUID();
        User user = new User("merchant@swiftdrop.lk", "hash", "Merchant", "+94770000000");
        user.setId(userId);
        user.addRole(customerRole);
        user.addRole(new RoleEntity(Role.MERCHANT, "Merchant role"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserResponse response = userService.grantMerchantRole(userId);

        assertThat(response.roles()).contains(Role.CUSTOMER, Role.MERCHANT);
        verify(userRepository, never()).save(any());
        verify(roleRepository, never()).findByRole(Role.MERCHANT);
    }

    @Test
    @DisplayName("Should batch lookup users by IDs successfully")
    void shouldBatchLookupUsersSuccessfully() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        User user1 = new User("user1@swiftdrop.lk", "hash", "User One", "+94771111111");
        user1.setId(id1);
        user1.addRole(customerRole);

        User user2 = new User("user2@swiftdrop.lk", "hash", "User Two", "+94772222222");
        user2.setId(id2);
        RoleEntity courierRole = new RoleEntity(Role.COURIER, "Courier role");
        user2.addRole(courierRole);

        when(userRepository.findAllByIdIn(List.of(id1, id2))).thenReturn(List.of(user1, user2));

        List<UserLookupResponse> results = userService.lookupUsers(List.of(id1, id2));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(id1);
        assertThat(results.get(0).email()).isEqualTo("user1@swiftdrop.lk");
        assertThat(results.get(0).fullName()).isEqualTo("User One");
        assertThat(results.get(0).name()).isEqualTo("User One");
        assertThat(results.get(0).roles()).containsExactly("CUSTOMER");

        assertThat(results.get(1).id()).isEqualTo(id2);
        assertThat(results.get(1).email()).isEqualTo("user2@swiftdrop.lk");
        assertThat(results.get(1).fullName()).isEqualTo("User Two");
        assertThat(results.get(1).name()).isEqualTo("User Two");
        assertThat(results.get(1).roles()).containsExactly("COURIER");
    }

    @Test
    @DisplayName("Should return empty list when batch lookup given empty or null collection")
    void shouldReturnEmptyListWhenLookupUsersGivenEmptyOrNull() {
        assertThat(userService.lookupUsers(null)).isEmpty();
        assertThat(userService.lookupUsers(List.of())).isEmpty();
        verify(userRepository, never()).findAllByIdIn(any());
    }

    @Test
    @DisplayName("Should lookup single user by ID successfully")
    void shouldLookupSingleUserSuccessfully() {
        UUID userId = UUID.randomUUID();
        User user = new User("single@swiftdrop.lk", "hash", "Single User", "+94773333333");
        user.setId(userId);
        user.addRole(customerRole);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserLookupResponse response = userService.lookupUser(userId);

        assertThat(response.id()).isEqualTo(userId);
        assertThat(response.email()).isEqualTo("single@swiftdrop.lk");
        assertThat(response.fullName()).isEqualTo("Single User");
        assertThat(response.name()).isEqualTo("Single User");
        assertThat(response.phone()).isEqualTo("+94773333333");
        assertThat(response.status()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when single user lookup not found")
    void shouldThrowWhenSingleUserNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.lookupUser(userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when single user lookup given null ID")
    void shouldThrowWhenLookupUserGivenNullId() {
        assertThatThrownBy(() -> userService.lookupUser(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

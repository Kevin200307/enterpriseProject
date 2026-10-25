package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.RegisterRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UpdateProfileRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.DuplicateEmailException;
import lk.ac.mrt.swiftdrop.identity_service.error.ResourceNotFoundException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
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
}

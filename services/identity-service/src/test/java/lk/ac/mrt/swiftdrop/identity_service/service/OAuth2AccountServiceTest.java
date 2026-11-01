package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.ConflictException;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.RoleRepository;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.OAuth2AccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2AccountServiceTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private JwtService jwtService;
    private RefreshTokenService refreshTokenService;
    private OAuth2AccountService accountService;

    private RoleEntity customerRole;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        jwtService = mock(JwtService.class);
        refreshTokenService = mock(RefreshTokenService.class);

        accountService = new OAuth2AccountServiceImpl(
                userRepository,
                roleRepository,
                jwtService,
                refreshTokenService
        );

        customerRole = new RoleEntity(Role.CUSTOMER, "Standard consumer role");
        when(roleRepository.findByRole(Role.CUSTOMER)).thenReturn(Optional.of(customerRole));
    }

    @Test
    @DisplayName("Should provision a new active CUSTOMER user with null password_hash when account does not exist")
    void shouldProvisionNewCustomerUserWhenNeitherSubNorEmailExists() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-new-123",
                "NewUser@SwiftDrop.lk",
                true,
                "New Google User",
                "New",
                "User",
                "https://lh3.googleusercontent.com/avatar.jpg"
        );

        when(userRepository.findByGoogleSub("google-sub-new-123")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("newuser@swiftdrop.lk")).thenReturn(Optional.empty());

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        User result = accountService.linkOrProvisionGoogleUser(claims);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User captured = userCaptor.getValue();

        assertThat(captured.getEmail()).isEqualTo("newuser@swiftdrop.lk");
        assertThat(captured.getPasswordHash()).isNull();
        assertThat(captured.getFullName()).isEqualTo("New Google User");
        assertThat(captured.getPhone()).isEqualTo("");
        assertThat(captured.getGoogleSub()).isEqualTo("google-sub-new-123");
        assertThat(captured.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(captured.isMustChangePassword()).isFalse();
        assertThat(captured.hasRole(Role.CUSTOMER)).isTrue();

        assertThat(result.getId()).isNotNull();
        assertThat(result.getEmail()).isEqualTo("newuser@swiftdrop.lk");
    }

    @Test
    @DisplayName("Should link google_sub to existing local account matched by verified email")
    void shouldLinkGoogleSubToExistingLocalAccountWhenMatchedByEmail() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-link-456",
                "existing@swiftdrop.lk",
                true,
                "Existing User",
                null,
                null,
                null
        );

        User localUser = new User("existing@swiftdrop.lk", "$2b$10$hashedPassword", "Existing User", "+94771234567");
        localUser.setId(UUID.randomUUID());
        localUser.addRole(customerRole);

        when(userRepository.findByGoogleSub("google-sub-link-456")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("existing@swiftdrop.lk")).thenReturn(Optional.of(localUser));
        when(userRepository.save(localUser)).thenReturn(localUser);

        User result = accountService.linkOrProvisionGoogleUser(claims);

        verify(userRepository).save(localUser);
        assertThat(result.getGoogleSub()).isEqualTo("google-sub-link-456");
        assertThat(result.getPasswordHash()).isEqualTo("$2b$10$hashedPassword");
        assertThat(result.getPhone()).isEqualTo("+94771234567");
    }

    @Test
    @DisplayName("Should return existing user directly without re-saving when matched by google_sub and email matches")
    void shouldReturnExistingUserWhenMatchedByGoogleSubAndEmailMatches() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-existing-789",
                "existing.oauth@swiftdrop.lk",
                true,
                "OAuth User",
                null,
                null,
                null
        );

        User existingOAuthUser = new User();
        existingOAuthUser.setId(UUID.randomUUID());
        existingOAuthUser.setEmail("existing.oauth@swiftdrop.lk");
        existingOAuthUser.setGoogleSub("google-sub-existing-789");
        existingOAuthUser.setStatus(UserStatus.ACTIVE);
        existingOAuthUser.addRole(customerRole);

        when(userRepository.findByGoogleSub("google-sub-existing-789")).thenReturn(Optional.of(existingOAuthUser));

        User result = accountService.linkOrProvisionGoogleUser(claims);

        verify(userRepository, never()).save(any());
        assertThat(result).isSameAs(existingOAuthUser);
    }

    @Test
    @DisplayName("Should throw ConflictException when google_sub is already linked to a different email")
    void shouldThrowConflictExceptionWhenGoogleSubIsAlreadyBoundToDifferentEmail() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-conflict",
                "new.email@swiftdrop.lk",
                true,
                "Hacker User",
                null,
                null,
                null
        );

        User existingVictim = new User();
        existingVictim.setId(UUID.randomUUID());
        existingVictim.setEmail("original.owner@swiftdrop.lk");
        existingVictim.setGoogleSub("google-sub-conflict");

        when(userRepository.findByGoogleSub("google-sub-conflict")).thenReturn(Optional.of(existingVictim));

        assertThatThrownBy(() -> accountService.linkOrProvisionGoogleUser(claims))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Google account is already linked to another user account");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ConflictException when local user account is already linked to a different google_sub")
    void shouldThrowConflictExceptionWhenEmailAccountIsAlreadyBoundToDifferentGoogleSub() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "new-google-sub-xyz",
                "target@swiftdrop.lk",
                true,
                "Target User",
                null,
                null,
                null
        );

        User existingLocalUser = new User();
        existingLocalUser.setId(UUID.randomUUID());
        existingLocalUser.setEmail("target@swiftdrop.lk");
        existingLocalUser.setGoogleSub("already-bound-sub-123");

        when(userRepository.findByGoogleSub("new-google-sub-xyz")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("target@swiftdrop.lk")).thenReturn(Optional.of(existingLocalUser));

        assertThatThrownBy(() -> accountService.linkOrProvisionGoogleUser(claims))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("User account is already linked to a different Google account");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw UserSuspendedException when existing user matched by google_sub is suspended")
    void shouldThrowUserSuspendedExceptionWhenExistingAccountBySubIsSuspended() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-suspended",
                "suspended@swiftdrop.lk",
                true,
                "Suspended User",
                null,
                null,
                null
        );

        User suspendedUser = new User();
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.setEmail("suspended@swiftdrop.lk");
        suspendedUser.setGoogleSub("google-sub-suspended");
        suspendedUser.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByGoogleSub("google-sub-suspended")).thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> accountService.linkOrProvisionGoogleUser(claims))
                .isInstanceOf(UserSuspendedException.class);
    }

    @Test
    @DisplayName("Should throw UserSuspendedException when matched account by email is suspended")
    void shouldThrowUserSuspendedExceptionWhenMatchedAccountByEmailIsSuspended() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-suspended-email",
                "suspended2@swiftdrop.lk",
                true,
                "Suspended User",
                null,
                null,
                null
        );

        User suspendedUser = new User();
        suspendedUser.setId(UUID.randomUUID());
        suspendedUser.setEmail("suspended2@swiftdrop.lk");
        suspendedUser.setStatus(UserStatus.SUSPENDED);

        when(userRepository.findByGoogleSub("google-sub-suspended-email")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("suspended2@swiftdrop.lk")).thenReturn(Optional.of(suspendedUser));

        assertThatThrownBy(() -> accountService.linkOrProvisionGoogleUser(claims))
                .isInstanceOf(UserSuspendedException.class);
    }

    @Test
    @DisplayName("Should throw InvalidTokenException when email_verified claim is false")
    void shouldThrowInvalidTokenExceptionWhenEmailNotVerified() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-unverified",
                "unverified@swiftdrop.lk",
                false,
                "Unverified User",
                null,
                null,
                null
        );

        assertThatThrownBy(() -> accountService.linkOrProvisionGoogleUser(claims))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Google account email is not verified");
    }

    @Test
    @DisplayName("Should authenticate Google user, link/provision, and generate access and refresh tokens")
    void shouldAuthenticateGoogleUserAndGenerateTokens() {
        GoogleUserClaims claims = new GoogleUserClaims(
                "google-sub-auth",
                "auth.user@swiftdrop.lk",
                true,
                "Auth User",
                null,
                null,
                null
        );

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("auth.user@swiftdrop.lk");
        user.setFullName("Auth User");
        user.setGoogleSub("google-sub-auth");
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(customerRole);

        when(userRepository.findByGoogleSub("google-sub-auth")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("mock.rs256.access.jwt");
        when(jwtService.getAccessTokenTtl()).thenReturn(Duration.ofSeconds(900));
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("mock-raw-refresh-token-opaque");

        LoginResult result = accountService.authenticateGoogleUser(claims);

        assertThat(result.authResponse().accessToken()).isEqualTo("mock.rs256.access.jwt");
        assertThat(result.authResponse().expiresIn()).isEqualTo(900L);
        assertThat(result.authResponse().user().email()).isEqualTo("auth.user@swiftdrop.lk");
        assertThat(result.rawRefreshToken()).isEqualTo("mock-raw-refresh-token-opaque");
    }
}

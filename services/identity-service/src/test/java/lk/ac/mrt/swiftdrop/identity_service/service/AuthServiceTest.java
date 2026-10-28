package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.LoginRequest;
import lk.ac.mrt.swiftdrop.identity_service.api.dto.AuthResponse;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidCredentialsException;
import lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException;
import lk.ac.mrt.swiftdrop.identity_service.error.UserSuspendedException;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String DUMMY_BCRYPT_HASH = "$2b$10$e8kgnfD.V27B5eG8q4lDhe6K3KfZ9Wq6O0a3/JvHcqkC4b/9iV.gK";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RateLimiterService rateLimiterService;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeUser;

    @BeforeEach
    void setUp() {
        RoleEntity customerRole = new RoleEntity(Role.CUSTOMER, "Customer role");
        activeUser = new User(
                "amandi.arangala@swiftdrop.lk",
                "$2b$10$realHashedPasswordStringHere",
                "Amandi Arangala",
                "+94771234567"
        );
        activeUser.setId(UUID.randomUUID());
        activeUser.addRole(customerRole);
        activeUser.setCreatedAt(Instant.now());
        activeUser.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("Should authenticate successfully when credentials are valid")
    void shouldAuthenticateSuccessfully() {
        LoginRequest request = new LoginRequest(" AMANDI.ARANGALA@swiftdrop.lk ", "ValidPassword123!");

        when(userRepository.findByEmailIgnoreCase("amandi.arangala@swiftdrop.lk"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("ValidPassword123!", activeUser.getPasswordHash()))
                .thenReturn(true);

        User authenticated = authService.authenticate(request);

        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getEmail()).isEqualTo("amandi.arangala@swiftdrop.lk");
        verify(rateLimiterService).checkRateLimit("amandi.arangala@swiftdrop.lk");
        verify(rateLimiterService).resetAttempts("amandi.arangala@swiftdrop.lk");
        verify(rateLimiterService, never()).recordFailedAttempt(any());
    }

    @Test
    @DisplayName("Should return LoginResult with access token and refresh token on login")
    void shouldReturnLoginResultOnLogin() {
        LoginRequest request = new LoginRequest("amandi.arangala@swiftdrop.lk", "ValidPassword123!");

        when(userRepository.findByEmailIgnoreCase("amandi.arangala@swiftdrop.lk"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("ValidPassword123!", activeUser.getPasswordHash()))
                .thenReturn(true);

        when(jwtService.generateAccessToken(activeUser)).thenReturn("signed-token");
        when(jwtService.getAccessTokenTtl()).thenReturn(java.time.Duration.ofMinutes(15));
        when(refreshTokenService.issueRefreshToken(activeUser)).thenReturn("sample-raw-refresh-token");

        LoginResult result = authService.login(request);

        assertThat(result).isNotNull();
        assertThat(result.rawRefreshToken()).isEqualTo("sample-raw-refresh-token");
        assertThat(result.authResponse().accessToken()).isEqualTo("signed-token");
        assertThat(result.authResponse().expiresIn()).isEqualTo(900L);
        assertThat(result.authResponse().user().id()).isEqualTo(activeUser.getId());
        assertThat(result.authResponse().user().email()).isEqualTo("amandi.arangala@swiftdrop.lk");
        assertThat(result.authResponse().user().roles()).contains(Role.CUSTOMER);
        verify(rateLimiterService).resetAttempts("amandi.arangala@swiftdrop.lk");
    }

    @Test
    @DisplayName("Should execute dummy BCrypt hash matching and record failed attempt when user is not found")
    void shouldExecuteDummyBCryptWhenUserNotFound() {
        LoginRequest request = new LoginRequest("nonexistent@swiftdrop.lk", "Password123!");

        when(userRepository.findByEmailIgnoreCase("nonexistent@swiftdrop.lk"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(rateLimiterService).checkRateLimit("nonexistent@swiftdrop.lk");
        // Verify constant-time anti-timing attack defense execution
        verify(passwordEncoder).matches("Password123!", DUMMY_BCRYPT_HASH);
        verify(rateLimiterService).recordFailedAttempt("nonexistent@swiftdrop.lk");
        verify(rateLimiterService, never()).resetAttempts(any());
    }

    @Test
    @DisplayName("Should execute dummy BCrypt hash matching and record failed attempt when user has null password (OAuth account)")
    void shouldExecuteDummyBCryptWhenUserHasNullPassword() {
        LoginRequest request = new LoginRequest("googleuser@swiftdrop.lk", "Password123!");

        User oauthUser = new User(
                "googleuser@swiftdrop.lk",
                null,
                "Google User",
                "+94771234567"
        );
        oauthUser.setId(UUID.randomUUID());
        oauthUser.setGoogleSub("google-123456");
        oauthUser.addRole(new RoleEntity(Role.CUSTOMER, "Customer"));
        oauthUser.setCreatedAt(Instant.now());
        oauthUser.setUpdatedAt(Instant.now());

        when(userRepository.findByEmailIgnoreCase("googleuser@swiftdrop.lk"))
                .thenReturn(Optional.of(oauthUser));

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(InvalidCredentialsException.class);

        // Anti-timing defense prevents detecting OAuth vs password account via response timing
        verify(passwordEncoder).matches("Password123!", DUMMY_BCRYPT_HASH);
        verify(rateLimiterService).recordFailedAttempt("googleuser@swiftdrop.lk");
        verify(rateLimiterService, never()).resetAttempts(any());
    }

    @Test
    @DisplayName("Should record failed attempt when password does not match")
    void shouldRecordFailedAttemptOnInvalidPassword() {
        LoginRequest request = new LoginRequest("amandi.arangala@swiftdrop.lk", "WrongPassword!");

        when(userRepository.findByEmailIgnoreCase("amandi.arangala@swiftdrop.lk"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("WrongPassword!", activeUser.getPasswordHash()))
                .thenReturn(false);

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(rateLimiterService).recordFailedAttempt("amandi.arangala@swiftdrop.lk");
        verify(rateLimiterService, never()).resetAttempts(any());
    }

    @Test
    @DisplayName("Should throw UserSuspendedException when account is suspended")
    void shouldThrowWhenUserSuspended() {
        activeUser.suspend();
        LoginRequest request = new LoginRequest("amandi.arangala@swiftdrop.lk", "ValidPassword123!");

        when(userRepository.findByEmailIgnoreCase("amandi.arangala@swiftdrop.lk"))
                .thenReturn(Optional.of(activeUser));
        when(passwordEncoder.matches("ValidPassword123!", activeUser.getPasswordHash()))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(UserSuspendedException.class)
                .hasMessage("User account has been suspended");

        verify(rateLimiterService, never()).resetAttempts(any());
    }

    @Test
    @DisplayName("Should fail fast without checking repository when rate limit is exceeded")
    void shouldFailFastWhenRateLimited() {
        LoginRequest request = new LoginRequest("attacker@swiftdrop.lk", "AnyPassword!");

        doThrow(new RateLimitExceededException(45))
                .when(rateLimiterService).checkRateLimit("attacker@swiftdrop.lk");

        assertThatThrownBy(() -> authService.authenticate(request))
                .isInstanceOf(RateLimitExceededException.class);

        verify(userRepository, never()).findByEmailIgnoreCase(any());
        verify(passwordEncoder, never()).matches(any(), any());
    }
}

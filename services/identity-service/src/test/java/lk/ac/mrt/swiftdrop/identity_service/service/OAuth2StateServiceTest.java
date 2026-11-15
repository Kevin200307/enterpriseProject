package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.error.InvalidOAuthStateException;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.OAuth2StateServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OAuth2StateServiceTest {

    private OAuth2StateService stateService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);

        stateService = new OAuth2StateServiceImpl(provider);
    }

    @Test
    @DisplayName("Should generate cryptographically secure URL-safe state and store associated data")
    void shouldGenerateSecureUrlSafeStateWithValidData() {
        String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSST-hhi";
        String redirectUri = "http://localhost:5173/oauth/callback";

        String state = stateService.generateAndStoreState(codeChallenge, redirectUri);

        assertThat(state).isNotBlank();
        assertThat(state).matches("^[A-Za-z0-9_-]+$"); // URL-safe base64
        assertThat(state.length()).isGreaterThanOrEqualTo(40);

        verify(valueOperations).set(
                eq("oauth2:state:" + state),
                eq(codeChallenge + "\u001F" + redirectUri),
                eq(Duration.ofMinutes(10))
        );
    }

    @Test
    @DisplayName("Should consume state from Redis and enforce single-use semantics")
    void shouldConsumeStateFromRedisWithSingleUseEnforcement() {
        String state = "test-oauth-state-12345";
        String codeChallenge = "test-code-challenge";
        String redirectUri = "http://localhost:5173/oauth/callback";
        String serialized = codeChallenge + "\u001F" + redirectUri;

        when(valueOperations.getAndDelete("oauth2:state:" + state)).thenReturn(serialized);

        OAuth2StateData stateData = stateService.consumeState(state);

        assertThat(stateData).isNotNull();
        assertThat(stateData.codeChallenge()).isEqualTo(codeChallenge);
        assertThat(stateData.redirectUri()).isEqualTo(redirectUri);

        // When Redis returns null on second attempt (already consumed)
        when(valueOperations.getAndDelete("oauth2:state:" + state)).thenReturn(null);

        assertThatThrownBy(() -> stateService.consumeState(state))
                .isInstanceOf(InvalidOAuthStateException.class)
                .hasMessageContaining("OAuth state is invalid or has expired");
    }

    @Test
    @DisplayName("Should fallback to in-memory cache when Redis is unavailable or fails")
    void shouldFallbackToInMemoryCacheWhenRedisFails() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null); // No Redis configured

        OAuth2StateService localStateService = new OAuth2StateServiceImpl(emptyProvider);

        String codeChallenge = "memory-challenge";
        String redirectUri = "http://localhost:5173/oauth/callback";

        String state = localStateService.generateAndStoreState(codeChallenge, redirectUri);
        assertThat(state).isNotBlank();

        // First consume succeeds
        OAuth2StateData data = localStateService.consumeState(state);
        assertThat(data.codeChallenge()).isEqualTo(codeChallenge);
        assertThat(data.redirectUri()).isEqualTo(redirectUri);

        // Second consume fails (enforcing single-use)
        assertThatThrownBy(() -> localStateService.consumeState(state))
                .isInstanceOf(InvalidOAuthStateException.class);
    }

    @Test
    @DisplayName("Should reject invalid or empty state values on consumption")
    void shouldRejectInvalidOrEmptyState() {
        assertThatThrownBy(() -> stateService.consumeState(null))
                .isInstanceOf(InvalidOAuthStateException.class);

        assertThatThrownBy(() -> stateService.consumeState("   "))
                .isInstanceOf(InvalidOAuthStateException.class);

        when(valueOperations.getAndDelete(anyString())).thenReturn(null);
        assertThatThrownBy(() -> stateService.consumeState("non-existent-state"))
                .isInstanceOf(InvalidOAuthStateException.class);
    }

    @Test
    @DisplayName("Should reject null or blank parameters during state generation")
    void shouldRejectNullOrBlankParametersDuringGeneration() {
        assertThatThrownBy(() -> stateService.generateAndStoreState(null, "http://localhost:5173/callback"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> stateService.generateAndStoreState("   ", "http://localhost:5173/callback"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> stateService.generateAndStoreState("challenge", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> stateService.generateAndStoreState("challenge", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should validate PKCE codeVerifier and codeChallenge according to RFC 7636 S256")
    void shouldValidatePkceCorrectlyAccordingToRfc7636() throws Exception {
        // RFC 7636 Appendix B test vector:
        // code_verifier: "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        // code_challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

        assertThat(stateService.validatePkce(codeVerifier, expectedChallenge)).isTrue();

        // Mismatched verifier
        assertThat(stateService.validatePkce("wrong-verifier-1234567890", expectedChallenge)).isFalse();

        // Blank or null values
        assertThat(stateService.validatePkce(null, expectedChallenge)).isFalse();
        assertThat(stateService.validatePkce(codeVerifier, null)).isFalse();
        assertThat(stateService.validatePkce("", expectedChallenge)).isFalse();
        assertThat(stateService.validatePkce(codeVerifier, "")).isFalse();
    }
}

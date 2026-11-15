package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.config.GoogleOAuth2Properties;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.OAuth2ExchangeException;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.GoogleOAuth2ServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleOAuth2ServiceTest {

    private static final String CLIENT_ID = "test-google-client-id.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "test-google-client-secret";
    private static final String TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/callback";
    private static final String JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String ISSUER = "https://accounts.google.com";

    private GoogleOAuth2Properties properties;
    private MockRestServiceServer mockServer;
    private JwtDecoder jwtDecoder;
    private GoogleOAuth2Service googleOAuth2Service;

    @BeforeEach
    void setUp() {
        properties = new GoogleOAuth2Properties(
                CLIENT_ID,
                CLIENT_SECRET,
                REDIRECT_URI,
                "https://accounts.google.com/o/oauth2/v2/auth",
                TOKEN_URI,
                "https://openidconnect.googleapis.com/v1/userinfo",
                JWK_SET_URI,
                ISSUER
        );

        RestClient.Builder restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        RestClient restClient = restClientBuilder.build();

        jwtDecoder = mock(JwtDecoder.class);
        googleOAuth2Service = new GoogleOAuth2ServiceImpl(properties, restClient, jwtDecoder);
    }

    @Test
    @DisplayName("Should successfully exchange authorization code for GoogleTokenResponse")
    void shouldExchangeAuthorizationCodeSuccessfully() {
        String code = "auth-code-123";
        String codeVerifier = "pkce-code-verifier-456";
        String customRedirectUri = "https://app.swiftdrop.lk/oauth/callback";

        String jsonResponse = """
                {
                    "access_token": "google-access-token-xyz",
                    "expires_in": 3600,
                    "scope": "openid email profile",
                    "token_type": "Bearer",
                    "id_token": "google.raw.id.token.jwt",
                    "refresh_token": "google-refresh-token-123"
                }
                """;

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=" + code)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=" + CLIENT_ID)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=" + CLIENT_SECRET)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code_verifier=" + codeVerifier)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        GoogleTokenResponse response = googleOAuth2Service.exchangeAuthorizationCode(code, codeVerifier, customRedirectUri);

        mockServer.verify();
        assertThat(response.accessToken()).isEqualTo("google-access-token-xyz");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.scope()).isEqualTo("openid email profile");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.idToken()).isEqualTo("google.raw.id.token.jwt");
        assertThat(response.refreshToken()).isEqualTo("google-refresh-token-123");
    }

    @Test
    @DisplayName("Should throw OAuth2ExchangeException when Google token endpoint returns 400 Bad Request")
    void shouldThrowOAuth2ExchangeExceptionWhenTokenEndpointReturns400() {
        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\",\"error_description\":\"Code expired\"}"));

        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("bad-code", "verifier", REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("Google token exchange failed with status: 400 BAD_REQUEST");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw OAuth2ExchangeException when response contains no id_token")
    void shouldThrowOAuth2ExchangeExceptionWhenResponseHasNoIdToken() {
        String jsonWithoutIdToken = """
                {
                    "access_token": "google-access-token-xyz",
                    "expires_in": 3600,
                    "token_type": "Bearer"
                }
                """;

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonWithoutIdToken, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("code", "verifier", REDIRECT_URI))
                .isInstanceOf(OAuth2ExchangeException.class)
                .hasMessageContaining("did not contain an id_token");

        mockServer.verify();
    }

    @Test
    @DisplayName("Should verify ID token successfully and extract all user claims")
    void shouldVerifyIdTokenSuccessfullyAndExtractClaims() {
        String rawToken = "valid.google.idtoken";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256", "kid", "google-key-id-1"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "109876543210123456789",
                        "email", "Amandi.Arangala@Gmail.com",
                        "email_verified", true,
                        "name", "Amandi Arangala",
                        "given_name", "Amandi",
                        "family_name", "Arangala",
                        "picture", "https://lh3.googleusercontent.com/a/photo.jpg"
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        GoogleUserClaims claims = googleOAuth2Service.verifyIdToken(rawToken);

        assertThat(claims.sub()).isEqualTo("109876543210123456789");
        assertThat(claims.email()).isEqualTo("amandi.arangala@gmail.com");
        assertThat(claims.emailVerified()).isTrue();
        assertThat(claims.name()).isEqualTo("Amandi Arangala");
        assertThat(claims.givenName()).isEqualTo("Amandi");
        assertThat(claims.familyName()).isEqualTo("Arangala");
        assertThat(claims.picture()).isEqualTo("https://lh3.googleusercontent.com/a/photo.jpg");
    }

    @Test
    @DisplayName("Should accept ID token with short issuer format 'accounts.google.com'")
    void shouldAcceptIdTokenWithShortIssuer() {
        String rawToken = "valid.short.issuer.token";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-short",
                        "email", "user@swiftdrop.lk",
                        "email_verified", "true"
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        GoogleUserClaims claims = googleOAuth2Service.verifyIdToken(rawToken);

        assertThat(claims.sub()).isEqualTo("google-sub-short");
        assertThat(claims.email()).isEqualTo("user@swiftdrop.lk");
        assertThat(claims.name()).isEqualTo("user");
    }

    @Test
    @DisplayName("Should fallback to given and family name when name claim is missing")
    void shouldFallbackToGivenAndFamilyNameWhenNameIsMissing() {
        String rawToken = "token.without.name";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-fallback",
                        "email", "john.doe@gmail.com",
                        "email_verified", true,
                        "given_name", "John",
                        "family_name", "Doe"
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        GoogleUserClaims claims = googleOAuth2Service.verifyIdToken(rawToken);

        assertThat(claims.name()).isEqualTo("John Doe");
    }

    @Test
    @DisplayName("Should fallback to email prefix when all name claims are missing")
    void shouldFallbackToEmailPrefixWhenAllNameClaimsAreMissing() {
        String rawToken = "token.no.name.at.all";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-email-fallback",
                        "email", "swiftcustomer42@gmail.com",
                        "email_verified", true
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        GoogleUserClaims claims = googleOAuth2Service.verifyIdToken(rawToken);

        assertThat(claims.name()).isEqualTo("swiftcustomer42");
    }

    @Test
    @DisplayName("Should reject ID token when issuer does not match Google accounts")
    void shouldRejectIdTokenWithInvalidIssuer() {
        String rawToken = "token.invalid.issuer";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://malicious-identity.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "sub-1",
                        "email", "evil@malicious.com",
                        "email_verified", true
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Invalid Google ID token issuer: https://malicious-identity.com");
    }

    @Test
    @DisplayName("Should reject ID token when audience does not match configured clientId")
    void shouldRejectIdTokenWithAudienceMismatch() {
        String rawToken = "token.audience.mismatch";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of("completely-different-client-id"),
                        "sub", "sub-1",
                        "email", "user@swiftdrop.lk",
                        "email_verified", true
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("audience mismatch");
    }

    @Test
    @DisplayName("Should reject ID token when email_verified is false")
    void shouldRejectIdTokenWhenEmailNotVerified() {
        String rawToken = "token.unverified.email";
        Instant now = Instant.now();

        Jwt mockJwt = new Jwt(
                rawToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "sub-1",
                        "email", "unverified@swiftdrop.lk",
                        "email_verified", false
                )
        );

        when(jwtDecoder.decode(rawToken)).thenReturn(mockJwt);

        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Google account email is not verified");
    }

    @Test
    @DisplayName("Should reject ID token when JwtDecoder throws JwtException (signature/expiry error)")
    void shouldRejectIdTokenWhenDecoderThrowsJwtException() {
        String rawToken = "expired.or.corrupted.token";

        when(jwtDecoder.decode(rawToken)).thenThrow(new BadJwtException("Token signature verification failed"));

        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("Google ID token validation failed: Token signature verification failed");
    }

    @Test
    @DisplayName("Should validate required input parameters")
    void shouldValidateInputParameters() {
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode(null, "verifier", REDIRECT_URI))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("   ", "verifier", REDIRECT_URI))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("code", null, REDIRECT_URI))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("code", "   ", REDIRECT_URI))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("code", "verifier", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> googleOAuth2Service.exchangeAuthorizationCode("code", "verifier", "   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken(null))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> googleOAuth2Service.verifyIdToken("   "))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Should coordinate exchange and verification atomically")
    void shouldCoordinateExchangeAndVerifyAtomically() {
        String code = "auth-code-atomic";
        String verifier = "verifier-atomic";
        String idToken = "id-token-atomic";

        String jsonResponse = """
                {
                    "access_token": "access-token-atomic",
                    "expires_in": 3600,
                    "token_type": "Bearer",
                    "id_token": "id-token-atomic"
                }
                """;

        mockServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        Instant now = Instant.now();
        Jwt mockJwt = new Jwt(
                idToken,
                now,
                now.plusSeconds(3600),
                Map.of("alg", "RS256"),
                Map.of(
                        "iss", "https://accounts.google.com",
                        "aud", List.of(CLIENT_ID),
                        "sub", "google-sub-atomic",
                        "email", "atomic@swiftdrop.lk",
                        "email_verified", true,
                        "name", "Atomic Tester"
                )
        );

        when(jwtDecoder.decode(idToken)).thenReturn(mockJwt);

        GoogleUserClaims claims = googleOAuth2Service.exchangeAndVerify(code, verifier, REDIRECT_URI);

        mockServer.verify();
        verify(jwtDecoder).decode(idToken);
        assertThat(claims.sub()).isEqualTo("google-sub-atomic");
        assertThat(claims.email()).isEqualTo("atomic@swiftdrop.lk");
        assertThat(claims.name()).isEqualTo("Atomic Tester");
    }
}

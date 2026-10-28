package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.config.GoogleOAuth2Properties;
import lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException;
import lk.ac.mrt.swiftdrop.identity_service.error.OAuth2ExchangeException;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleOAuth2Service;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleTokenResponse;
import lk.ac.mrt.swiftdrop.identity_service.service.GoogleUserClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Implementation of GoogleOAuth2Service providing RFC 7636 PKCE authorization code exchange
 * and OpenID Connect ID token signature and claims verification.
 */
@Service
public class GoogleOAuth2ServiceImpl implements GoogleOAuth2Service {

    private static final Logger log = LoggerFactory.getLogger(GoogleOAuth2ServiceImpl.class);
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";
    private static final String GOOGLE_ISSUER_SHORT = "accounts.google.com";

    private final GoogleOAuth2Properties googleProperties;
    private final RestClient restClient;
    private final JwtDecoder googleJwtDecoder;

    @Autowired
    public GoogleOAuth2ServiceImpl(GoogleOAuth2Properties googleProperties) {
        this(
                googleProperties,
                RestClient.create(),
                createDefaultJwtDecoder(googleProperties.jwkSetUri())
        );
    }

    public GoogleOAuth2ServiceImpl(
            GoogleOAuth2Properties googleProperties,
            RestClient restClient,
            JwtDecoder googleJwtDecoder
    ) {
        this.googleProperties = Objects.requireNonNull(googleProperties, "googleProperties must not be null");
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
        this.googleJwtDecoder = Objects.requireNonNull(googleJwtDecoder, "googleJwtDecoder must not be null");
    }

    @Override
    public GoogleTokenResponse exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be null or blank");
        }
        if (codeVerifier == null || codeVerifier.isBlank()) {
            throw new IllegalArgumentException("codeVerifier must not be null or blank");
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException("redirectUri must not be null or blank");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code.trim());
        formData.add("client_id", googleProperties.clientId());
        formData.add("client_secret", googleProperties.clientSecret());
        formData.add("redirect_uri", redirectUri.trim());
        formData.add("grant_type", "authorization_code");
        formData.add("code_verifier", codeVerifier.trim());

        try {
            GoogleTokenResponse response = restClient.post()
                    .uri(googleProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (response == null || response.idToken() == null || response.idToken().isBlank()) {
                throw new OAuth2ExchangeException("Google token exchange response did not contain an id_token");
            }
            return response;
        } catch (OAuth2ExchangeException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.error("Google token exchange failed [status={}, body={}]", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OAuth2ExchangeException("Google token exchange failed with status: " + ex.getStatusCode(), ex);
        } catch (RestClientException ex) {
            log.error("Google token exchange communication failure: {}", ex.getMessage());
            throw new OAuth2ExchangeException("Failed to communicate with Google token endpoint", ex);
        }
    }

    @Override
    public GoogleUserClaims verifyIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new InvalidTokenException("Google id_token must not be null or blank");
        }

        Jwt jwt;
        try {
            jwt = googleJwtDecoder.decode(idToken.trim());
        } catch (JwtException ex) {
            log.warn("Google ID token decoding/verification failed: {}", ex.getMessage());
            throw new InvalidTokenException("Google ID token validation failed: " + ex.getMessage());
        }

        // 1. Verify Issuer (RFC 7519 §4.1.1 / OIDC Core §3.1.3.7)
        String issuer = jwt.getClaimAsString("iss");
        if (issuer == null || (!issuer.equals(GOOGLE_ISSUER_HTTPS) && !issuer.equals(GOOGLE_ISSUER_SHORT))) {
            throw new InvalidTokenException("Invalid Google ID token issuer: " + issuer);
        }

        // 2. Verify Audience (RFC 7519 §4.1.3 / OIDC Core §3.1.3.7)
        List<String> audience = jwt.getAudience();
        if (audience == null || !audience.contains(googleProperties.clientId())) {
            throw new InvalidTokenException("Google ID token audience mismatch: expected " + googleProperties.clientId());
        }

        // 3. Verify Subject (Google unique user id)
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new InvalidTokenException("Google ID token missing subject (sub) claim");
        }

        // 4. Verify Email
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new InvalidTokenException("Google ID token missing email claim");
        }

        // 5. Verify Email Verified
        Object emailVerifiedClaim = jwt.getClaim("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(emailVerifiedClaim)
                || "true".equalsIgnoreCase(String.valueOf(emailVerifiedClaim));
        if (!emailVerified) {
            throw new InvalidTokenException("Google account email is not verified");
        }

        // 6. Extract profile attributes with graceful fallbacks
        String name = jwt.getClaimAsString("name");
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        String picture = jwt.getClaimAsString("picture");

        if (name == null || name.isBlank()) {
            if (givenName != null && !givenName.isBlank()) {
                name = (familyName != null && !familyName.isBlank())
                        ? (givenName.trim() + " " + familyName.trim())
                        : givenName.trim();
            } else {
                int atIndex = email.indexOf('@');
                name = (atIndex > 0) ? email.substring(0, atIndex) : email;
            }
        }

        return new GoogleUserClaims(
                sub.trim(),
                email.trim().toLowerCase(Locale.ROOT),
                true,
                name.trim(),
                givenName != null ? givenName.trim() : null,
                familyName != null ? familyName.trim() : null,
                picture
        );
    }

    @Override
    public GoogleUserClaims exchangeAndVerify(String code, String codeVerifier, String redirectUri) {
        GoogleTokenResponse tokenResponse = exchangeAuthorizationCode(code, codeVerifier, redirectUri);
        return verifyIdToken(tokenResponse.idToken());
    }

    private static JwtDecoder createDefaultJwtDecoder(String jwkSetUri) {
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
    }
}

package lk.ac.mrt.swiftdrop.identity_service.service;

/**
 * Service managing Google OAuth 2.0 / OIDC token exchange and ID token verification.
 */
public interface GoogleOAuth2Service {

    /**
     * Exchanges an authorization code and PKCE code verifier for Google tokens via Google's token endpoint.
     *
     * @param code the authorization code received from Google callback
     * @param codeVerifier the original unhashed PKCE code verifier
     * @param redirectUri the redirect URI matching the initial authorization request
     * @return the token response containing access_token and id_token
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.OAuth2ExchangeException if communication or token exchange fails
     */
    GoogleTokenResponse exchangeAuthorizationCode(String code, String codeVerifier, String redirectUri);

    /**
     * Verifies the cryptographic signature, expiration, issuer, and audience of a Google ID token,
     * and extracts verified user claims.
     *
     * @param idToken the raw signed Google ID token (JWT)
     * @return verified user claims
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.InvalidTokenException if token is invalid, expired, or claims mismatch
     */
    GoogleUserClaims verifyIdToken(String idToken);

    /**
     * Orchestrates token exchange and ID token verification in a single atomic flow.
     *
     * @param code the authorization code
     * @param codeVerifier the PKCE code verifier
     * @param redirectUri the redirect URI
     * @return verified user claims
     */
    GoogleUserClaims exchangeAndVerify(String code, String codeVerifier, String redirectUri);
}

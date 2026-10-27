package lk.ac.mrt.swiftdrop.identity_service.service;

/**
 * Service managing OAuth 2.0 PKCE state parameter generation, storage, single-use consumption,
 * and RFC 7636 S256 code challenge verification 
 */
public interface OAuth2StateService {

    /**
     * Generates a cryptographically secure random state parameter (32 bytes entropy),
     * stores it linked with the PKCE code_challenge and redirect_uri with a 10-minute TTL,
     * and returns the state string.
     *
     * @param codeChallenge the RFC 7636 S256 code challenge provided by the SPA client
     * @param redirectUri the redirect URI provided by the SPA client
     * @return cryptographically secure URL-safe state string
     */
    String generateAndStoreState(String codeChallenge, String redirectUri);

    /**
     * Consumes an OAuth 2.0 state parameter: retrieves the associated OAuth2StateData
     * and immediately deletes it to enforce strict single-use semantics.
     *
     * @param state the state parameter received in the callback
     * @return the associated OAuth2StateData
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.InvalidOAuthStateException if state is null, blank, expired, or invalid
     */
    OAuth2StateData consumeState(String state);

    /**
     * Validates that the provided codeVerifier matches the stored codeChallenge
     * using the RFC 7636 S256 method: codeChallenge == BASE64URL(SHA-256(ASCII(codeVerifier))).
     * Comparison is executed in constant time.
     *
     * @param codeVerifier the plaintext code verifier sent in the token callback
     * @param codeChallenge the S256 code challenge stored during authorization initiation
     * @return true if valid, false otherwise
     */
    boolean validatePkce(String codeVerifier, String codeChallenge);
}

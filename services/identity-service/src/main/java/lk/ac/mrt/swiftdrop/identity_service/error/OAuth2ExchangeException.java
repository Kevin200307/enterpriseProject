package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

/**
 * Thrown when exchanging an authorization code with Google's token endpoint fails.
 * Maps to UNAUTHENTICATED (401) problem details.
 */
public class OAuth2ExchangeException extends AppException {

    public OAuth2ExchangeException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }

    public OAuth2ExchangeException(String message, Throwable cause) {
        super(ErrorCode.UNAUTHENTICATED, message, cause);
    }
}

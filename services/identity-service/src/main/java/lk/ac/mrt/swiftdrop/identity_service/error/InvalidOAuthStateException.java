package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

/**
 * Thrown when an OAuth2 state parameter is missing, expired, mismatched, or replayed 
 * Maps UNAUTHENTICATED problem details.
 */
public class InvalidOAuthStateException extends AppException {

    public InvalidOAuthStateException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}

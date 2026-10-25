package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class InvalidTokenException extends AppException {

    public InvalidTokenException(String message) {
        super(ErrorCode.UNAUTHENTICATED, message);
    }
}

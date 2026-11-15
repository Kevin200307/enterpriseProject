package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class InvalidCredentialsException extends AppException {

    public InvalidCredentialsException() {
        super(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
    }

    public InvalidCredentialsException(String message) {
        super(ErrorCode.INVALID_CREDENTIALS, message);
    }
}

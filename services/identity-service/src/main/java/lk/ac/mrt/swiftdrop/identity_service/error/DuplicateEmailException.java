package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class DuplicateEmailException extends AppException {

    public DuplicateEmailException() {
        super(ErrorCode.CONFLICT, "A user with this email address already exists");
    }

    public DuplicateEmailException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}

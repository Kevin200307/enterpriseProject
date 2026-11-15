package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class ConflictException extends AppException {

    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}

package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class UserSuspendedException extends AppException {

    public UserSuspendedException() {
        super(ErrorCode.ACCESS_DENIED, "User account has been suspended");
    }

    public UserSuspendedException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }
}

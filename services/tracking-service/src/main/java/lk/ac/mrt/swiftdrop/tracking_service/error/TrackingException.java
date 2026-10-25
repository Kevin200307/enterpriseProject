package lk.ac.mrt.swiftdrop.tracking_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lombok.Getter;

@Getter
public class TrackingException extends RuntimeException {

    private final ErrorCode errorCode;

    public TrackingException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
    }
}

package lk.ac.mrt.swiftdrop.parcel_service.api;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class ParcelException extends RuntimeException {
    private final ErrorCode code;
    public ParcelException(ErrorCode code, String message) { super(message); this.code = code; }
    public ErrorCode code() { return code; }
}

package lk.ac.mrt.swiftdrop.merchant_service.service.exception;

public class IdentityServiceException extends RuntimeException {
    public IdentityServiceException(String message) {
        super(message);
    }

    public IdentityServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

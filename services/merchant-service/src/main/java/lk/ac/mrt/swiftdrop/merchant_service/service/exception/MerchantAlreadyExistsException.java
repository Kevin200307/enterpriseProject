package lk.ac.mrt.swiftdrop.merchant_service.service.exception;

public class MerchantAlreadyExistsException extends RuntimeException {
    public MerchantAlreadyExistsException(String message) {
        super(message);
    }
}

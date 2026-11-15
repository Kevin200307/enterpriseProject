package lk.ac.mrt.swiftdrop.pricing_service.service;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class PricingException extends RuntimeException {
    private final ErrorCode code;
    public PricingException(ErrorCode code, String message) { super(message); this.code = code; }
    public ErrorCode code() { return code; }
}
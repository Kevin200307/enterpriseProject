package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;

public class RateLimitExceededException extends AppException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, "Too many requests. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMITED, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

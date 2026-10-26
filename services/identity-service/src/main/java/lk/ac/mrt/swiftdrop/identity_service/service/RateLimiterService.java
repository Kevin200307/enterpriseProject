package lk.ac.mrt.swiftdrop.identity_service.service;

/**
 * Distributed rate limiter service protecting authentication endpoints from brute-force attacks.
 */
public interface RateLimiterService {

    /**
     * Verifies that the client has not exceeded the allowed failed attempt threshold.
     *
     * @param key unique identifier (e.g. user email or client IP)
     * @throws lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException if threshold exceeded
     */
    void checkRateLimit(String key);

    /**
     * Increments the failed attempt counter for the key, setting expiration if first attempt.
     *
     * @param key unique identifier
     */
    void recordFailedAttempt(String key);

    /**
     * Resets/clears the failed attempt counter upon successful authentication.
     *
     * @param key unique identifier
     */
    void resetAttempts(String key);
}

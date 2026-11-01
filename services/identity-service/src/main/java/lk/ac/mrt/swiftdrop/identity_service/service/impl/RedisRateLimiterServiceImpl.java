package lk.ac.mrt.swiftdrop.identity_service.service.impl;

import lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException;
import lk.ac.mrt.swiftdrop.identity_service.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Redis-backed distributed rate limiter implementing an atomic counter sliding/fixed window
 * Protects login endpoints against brute-force attacks 
 */
@Service
public class RedisRateLimiterServiceImpl implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterServiceImpl.class);
    private static final String REDIS_PREFIX = "auth:failed:";

    private final StringRedisTemplate redisTemplate;
    private final int maxFailedAttempts;
    private final long windowSeconds;

    public RedisRateLimiterServiceImpl(
            StringRedisTemplate redisTemplate,
            @Value("${app.security.rate-limit.max-failed-login-attempts:5}") int maxFailedAttempts,
            @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.maxFailedAttempts = maxFailedAttempts;
        this.windowSeconds = windowSeconds;
    }

    @Override
    public void checkRateLimit(String key) {
        try {
            String redisKey = buildKey(key);
            String countStr = redisTemplate.opsForValue().get(redisKey);

            if (countStr != null) {
                long currentCount = Long.parseLong(countStr);
                if (currentCount >= maxFailedAttempts) {
                    Long ttl = redisTemplate.getExpire(redisKey);
                    long retryAfter = (ttl != null && ttl > 0) ? ttl : windowSeconds;

                    log.warn("Rate limit exceeded for key [key={}, attempts={}, retryAfter={}s]",
                            maskKey(key), currentCount, retryAfter);
                    throw new RateLimitExceededException(retryAfter);
                }
            }
        } catch (RateLimitExceededException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Redis error while checking rate limit for key [{}]. Degrading gracefully.", maskKey(key), ex);
        }
    }

    @Override
    public void recordFailedAttempt(String key) {
        try {
            String redisKey = buildKey(key);
            Long count = redisTemplate.opsForValue().increment(redisKey);

            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(windowSeconds));
            }

            log.debug("Recorded failed attempt for key [key={}, count={}/{}]",
                    maskKey(key), count, maxFailedAttempts);
        } catch (Exception ex) {
            log.error("Redis error while recording failed attempt for key [{}].", maskKey(key), ex);
        }
    }

    @Override
    public void resetAttempts(String key) {
        try {
            String redisKey = buildKey(key);
            redisTemplate.delete(redisKey);
            log.debug("Reset failed attempts counter for key [{}]", maskKey(key));
        } catch (Exception ex) {
            log.error("Redis error while resetting attempts for key [{}].", maskKey(key), ex);
        }
    }

    private String buildKey(String rawKey) {
        return REDIS_PREFIX + rawKey.trim().toLowerCase();
    }

    private String maskKey(String key) {
        if (key == null || key.length() < 3) return "***";
        int atIdx = key.indexOf('@');
        if (atIdx > 1) {
            return key.charAt(0) + "***@" + key.substring(atIdx + 1);
        }
        return key.substring(0, 2) + "***";
    }
}

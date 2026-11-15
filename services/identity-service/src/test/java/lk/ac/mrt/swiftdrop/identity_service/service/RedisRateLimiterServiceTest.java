package lk.ac.mrt.swiftdrop.identity_service.service;

import lk.ac.mrt.swiftdrop.identity_service.error.RateLimitExceededException;
import lk.ac.mrt.swiftdrop.identity_service.service.impl.RedisRateLimiterServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisRateLimiterServiceImpl rateLimiterService;

    @BeforeEach
    void setUp() {
        rateLimiterService = new RedisRateLimiterServiceImpl(redisTemplate, 5, 60);
    }

    @Test
    @DisplayName("Should pass rate limit check when attempts count is below max limit")
    void shouldPassWhenUnderLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:failed:user@swiftdrop.lk")).thenReturn("3");

        assertThatCode(() -> rateLimiterService.checkRateLimit("user@swiftdrop.lk"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should throw RateLimitExceededException when attempts reach 5")
    void shouldThrowWhenLimitExceeded() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("auth:failed:user@swiftdrop.lk")).thenReturn("5");
        when(redisTemplate.getExpire("auth:failed:user@swiftdrop.lk")).thenReturn(45L);

        assertThatThrownBy(() -> rateLimiterService.checkRateLimit("user@swiftdrop.lk"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(ex -> {
                    RateLimitExceededException rle = (RateLimitExceededException) ex;
                    assertThat(rle.getRetryAfterSeconds()).isEqualTo(45L);
                });
    }

    @Test
    @DisplayName("Should set key expiration on first failed attempt")
    void shouldSetExpirationOnFirstFailure() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:failed:user@swiftdrop.lk")).thenReturn(1L);

        rateLimiterService.recordFailedAttempt("user@swiftdrop.lk");

        verify(redisTemplate).expire(eq("auth:failed:user@swiftdrop.lk"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("Should not overwrite expiration on subsequent failed attempts")
    void shouldNotOverwriteExpirationOnSubsequentFailures() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("auth:failed:user@swiftdrop.lk")).thenReturn(2L);

        rateLimiterService.recordFailedAttempt("user@swiftdrop.lk");

        verify(redisTemplate, never()).expire(any(), any(Duration.class));
    }

    @Test
    @DisplayName("Should delete key upon reset")
    void shouldDeleteKeyOnReset() {
        rateLimiterService.resetAttempts("user@swiftdrop.lk");

        verify(redisTemplate).delete("auth:failed:user@swiftdrop.lk");
    }

    @Test
    @DisplayName("Should degrade gracefully when Redis fails")
    void shouldDegradeGracefullyOnRedisFailure() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        assertThatCode(() -> rateLimiterService.checkRateLimit("user@swiftdrop.lk"))
                .doesNotThrowAnyException();
    }
}

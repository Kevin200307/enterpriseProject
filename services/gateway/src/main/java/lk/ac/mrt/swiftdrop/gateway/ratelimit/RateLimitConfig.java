package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

	@Bean
	RedisRateLimiter loginRateLimiter() {
		return limiter(5, 300);
	}

	@Bean
	RedisRateLimiter publicTrackingRateLimiter() {
		return limiter(30, 1_800);
	}

	@Bean
	@Primary
	RedisRateLimiter defaultApiRateLimiter() {
		return limiter(100, 6_000);
	}

	private RedisRateLimiter limiter(int tokensPerMinute, long burstCapacity) {
		RedisRateLimiter limiter = new RedisRateLimiter(tokensPerMinute, burstCapacity, 60);
		limiter.setIncludeHeaders(true);
		return limiter;
	}
}

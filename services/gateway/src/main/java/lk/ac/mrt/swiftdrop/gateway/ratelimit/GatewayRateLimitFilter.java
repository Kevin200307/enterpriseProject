package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import java.util.Map;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.gateway.error.GatewayProblemWriter;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/** Applies Redis token buckets before proxying API traffic. */
@Component
public final class GatewayRateLimitFilter implements GlobalFilter, Ordered {

	private static final Logger LOGGER = LoggerFactory.getLogger(GatewayRateLimitFilter.class);
	private static final String RETRY_AFTER_SECONDS = "60";

	private final GatewayKeyResolver keyResolver;
	private final GatewayProblemWriter problemWriter;
	private final RateLimiter<?> loginRateLimiter;
	private final RateLimiter<?> publicTrackingRateLimiter;
	private final RateLimiter<?> defaultApiRateLimiter;

	public GatewayRateLimitFilter(
			GatewayKeyResolver keyResolver,
			GatewayProblemWriter problemWriter,
			@Qualifier("loginRateLimiter") RateLimiter<?> loginRateLimiter,
			@Qualifier("publicTrackingRateLimiter") RateLimiter<?> publicTrackingRateLimiter,
			@Qualifier("defaultApiRateLimiter") RateLimiter<?> defaultApiRateLimiter) {
		this.keyResolver = keyResolver;
		this.problemWriter = problemWriter;
		this.loginRateLimiter = loginRateLimiter;
		this.publicTrackingRateLimiter = publicTrackingRateLimiter;
		this.defaultApiRateLimiter = defaultApiRateLimiter;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		Policy policy = policyFor(exchange);
		if (policy == null) {
			return chain.filter(exchange);
		}

		return keyResolver.resolve(exchange)
				.flatMap(key -> policy.rateLimiter().isAllowed(policy.id(), key)
						.onErrorResume(exception -> {
							LOGGER.warn(
									"Rate limiting unavailable; allowing request for policy {} ({})",
									policy.id(),
									exception.getClass().getSimpleName());
							return Mono.just(new RateLimiter.Response(true, Map.of()));
						})
						.flatMap(response -> handleResponse(exchange, chain, response)));
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE + 10;
	}

	private Mono<Void> handleResponse(
			ServerWebExchange exchange,
			GatewayFilterChain chain,
			RateLimiter.Response response) {

		response.getHeaders().forEach((name, value) -> exchange.getResponse().getHeaders().set(name, value));
		if (response.isAllowed()) {
			return chain.filter(exchange);
		}

		exchange.getResponse().getHeaders().set("Retry-After", RETRY_AFTER_SECONDS);
		return problemWriter.write(
				exchange,
				ErrorCode.RATE_LIMITED,
				"The request rate limit has been exceeded",
				RequestIdFilter.requestId(exchange));
	}

	private Policy policyFor(ServerWebExchange exchange) {
		String path = exchange.getRequest().getPath().value();
		HttpMethod method = exchange.getRequest().getMethod();
		if (method == HttpMethod.POST && path.equals("/api/v1/auth/login")) {
			return new Policy("login", loginRateLimiter);
		}
		if (method == HttpMethod.GET && isPublicTrackingPath(path)) {
			return new Policy("public-tracking", publicTrackingRateLimiter);
		}
		if (path.startsWith("/api/v1/")) {
			return new Policy("default-api", defaultApiRateLimiter);
		}
		return null;
	}

	private boolean isPublicTrackingPath(String path) {
		String prefix = "/api/v1/tracking/";
		return path.startsWith(prefix)
				&& path.length() > prefix.length()
				&& path.indexOf('/', prefix.length()) < 0;
	}

	private record Policy(String id, RateLimiter<?> rateLimiter) {
	}
}

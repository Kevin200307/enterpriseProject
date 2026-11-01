package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.jayway.jsonpath.JsonPath;
import lk.ac.mrt.swiftdrop.gateway.error.GatewayProblemWriter;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayRateLimitFilterTests {

	private final GatewayKeyResolver keyResolver = new GatewayKeyResolver();
	private final GatewayProblemWriter problemWriter =
			new GatewayProblemWriter(JsonMapper.builder().build());
	private final RateLimiter<?> loginLimiter = mock(RateLimiter.class);
	private final RateLimiter<?> trackingLimiter = mock(RateLimiter.class);
	private final RateLimiter<?> defaultLimiter = mock(RateLimiter.class);

	private GatewayRateLimitFilter filter;

	@BeforeEach
	void createFilter() {
		filter = new GatewayRateLimitFilter(
				keyResolver,
				problemWriter,
				loginLimiter,
				trackingLimiter,
				defaultLimiter);
	}

	@Test
	void rejectedLoginReturnsRateLimitedProblemAndRetryAfter() {
		MockServerWebExchange exchange = exchange(MockServerHttpRequest.post("/api/v1/auth/login"));
		when(loginLimiter.isAllowed("login", "ip:192.0.2.10"))
				.thenReturn(Mono.just(new RateLimiter.Response(false, Map.of("X-RateLimit-Remaining", "0"))));

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(
				new AssertionError("rejected request must not reach the route"))))
				.verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
		assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(JsonPath.read(body, "$.errorCode").toString()).isEqualTo("RATE_LIMITED");
		assertThat(JsonPath.read(body, "$.traceId").toString()).isEqualTo("rate-request");
	}

	@Test
	void rejectedPublicTrackingReturnsRateLimitedProblemAndRetryAfter() {
		MockServerWebExchange exchange = exchange(
				MockServerHttpRequest.get("/api/v1/tracking/SD-12345678"));
		when(trackingLimiter.isAllowed("public-tracking", "ip:192.0.2.10"))
				.thenReturn(Mono.just(new RateLimiter.Response(false, Map.of("X-RateLimit-Remaining", "0"))));

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(
				new AssertionError("rejected request must not reach the route"))))
				.verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
		assertThat(exchange.getResponse().getHeaders().getFirst("X-RateLimit-Remaining")).isEqualTo("0");
		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(JsonPath.read(body, "$.errorCode").toString()).isEqualTo("RATE_LIMITED");
		assertThat(JsonPath.read(body, "$.traceId").toString()).isEqualTo("rate-request");
		assertThat(JsonPath.read(body, "$.instance").toString())
				.isEqualTo("/api/v1/tracking/SD-12345678");
	}

	@Test
	void publicTrackingAndNestedTrackingUseDifferentPolicies() {
		MockServerWebExchange publicExchange = exchange(
				MockServerHttpRequest.get("/api/v1/tracking/SD-123"));
		MockServerWebExchange nestedExchange = exchange(
				MockServerHttpRequest.get("/api/v1/tracking/SD-123/events"));
		when(trackingLimiter.isAllowed("public-tracking", "ip:192.0.2.10"))
				.thenReturn(Mono.just(new RateLimiter.Response(true, Map.of())));
		when(defaultLimiter.isAllowed("default-api", "ip:192.0.2.10"))
				.thenReturn(Mono.just(new RateLimiter.Response(true, Map.of())));

		StepVerifier.create(filter.filter(publicExchange, ignored -> Mono.empty())).verifyComplete();
		StepVerifier.create(filter.filter(nestedExchange, ignored -> Mono.empty())).verifyComplete();

		verify(trackingLimiter).isAllowed("public-tracking", "ip:192.0.2.10");
		verify(defaultLimiter).isAllowed("default-api", "ip:192.0.2.10");
	}

	@Test
	void redisFailureFailsOpenAndInvokesRouteOnce() {
		MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/v1/parcels"));
		when(defaultLimiter.isAllowed("default-api", "ip:192.0.2.10"))
				.thenReturn(Mono.error(new IllegalStateException("redis unavailable")));
		AtomicBoolean invoked = new AtomicBoolean();

		StepVerifier.create(filter.filter(exchange, ignored -> {
			assertThat(invoked.compareAndSet(false, true)).isTrue();
			return Mono.empty();
		})).verifyComplete();

		assertThat(invoked).isTrue();
	}

	@Test
	void nonApiRequestIsNotRateLimited() {
		MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/actuator/health"));

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

		verify(loginLimiter, never()).isAllowed("login", "ip:192.0.2.10");
		verify(trackingLimiter, never()).isAllowed("public-tracking", "ip:192.0.2.10");
		verify(defaultLimiter, never()).isAllowed("default-api", "ip:192.0.2.10");
	}

	private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
		MockServerWebExchange exchange = MockServerWebExchange.from(request
				.remoteAddress(new InetSocketAddress("192.0.2.10", 43123))
				.build());
		exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "rate-request");
		return exchange;
	}
}

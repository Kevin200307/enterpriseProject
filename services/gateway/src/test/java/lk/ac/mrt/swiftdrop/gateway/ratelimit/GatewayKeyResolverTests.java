package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

class GatewayKeyResolverTests {

	private final GatewayKeyResolver resolver = new GatewayKeyResolver();

	@Test
	void keysAuthenticatedTrafficByValidatedSubject() {
		Jwt jwt = new Jwt(
				"token",
				Instant.now(),
				Instant.now().plusSeconds(60),
				Map.of("alg", "RS256"),
				Map.of("sub", "user-123"));
		ServerWebExchange exchange = anonymousExchange().mutate()
				.principal(Mono.just(new JwtAuthenticationToken(jwt)))
				.build();

		StepVerifier.create(resolver.resolve(exchange))
				.expectNext("user:user-123")
				.verifyComplete();
	}

	@Test
	void keysAnonymousTrafficBySocketAddressNotForwardedHeaders() {
		MockServerWebExchange exchange = anonymousExchange();

		StepVerifier.create(resolver.resolve(exchange))
				.assertNext(key -> assertThat(key).isEqualTo("ip:192.0.2.10"))
				.verifyComplete();
	}

	private MockServerWebExchange anonymousExchange() {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/tracking/SD-123")
				.remoteAddress(new InetSocketAddress("192.0.2.10", 43123))
				.header("X-Forwarded-For", "203.0.113.99"));
	}
}

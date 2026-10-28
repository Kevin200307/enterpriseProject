package lk.ac.mrt.swiftdrop.gateway.error;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.main.allow-bean-definition-overriding=true")
@Import(GatewayCircuitBreakerIntegrationTests.TestSecurityConfiguration.class)
class GatewayCircuitBreakerIntegrationTests {

	private static final DisposableServer SLOW_UPSTREAM = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes.route(
					request -> true,
					(request, response) -> response.sendString(
							Mono.delay(Duration.ofSeconds(5)).thenReturn("too late"))))
			.bindNow();

	@LocalServerPort
	private int gatewayPort;

	private WebTestClient client;

	@TestConfiguration(proxyBeanMethods = false)
	static class TestSecurityConfiguration {

		@Bean
		SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
			return http
					.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
					.build();
		}
	}

	@DynamicPropertySource
	static void upstreamProperties(DynamicPropertyRegistry registry) {
		registry.add("gateway.services.parcel", () -> "http://127.0.0.1:1");
		registry.add("gateway.services.pricing", () -> "http://127.0.0.1:" + SLOW_UPSTREAM.port());
		registry.add(
				"spring.cloud.gateway.server.webflux.httpclient.response-timeout",
				() -> "100ms");
	}

	@BeforeEach
	void createClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + gatewayPort)
				.responseTimeout(Duration.ofSeconds(10))
				.build();
	}

	@AfterAll
	static void stopUpstream() {
		SLOW_UPSTREAM.disposeNow();
	}

	@Test
	void connectionRefusalReturnsAnUpstreamProblem() {
		assertUpstreamProblem("/api/v1/parcels/parcel-123", "refused-request");
	}

	@Test
	void responseTimeoutReturnsAnUpstreamProblem() {
		assertUpstreamProblem("/api/v1/pricing/rules", "timeout-request");
	}

	private void assertUpstreamProblem(String path, String requestId) {
		client.get()
				.uri(path)
				.header("X-Request-Id", requestId)
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader().valueEquals("X-Request-Id", requestId)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UPSTREAM_UNAVAILABLE")
				.jsonPath("$.traceId").isEqualTo(requestId)
				.jsonPath("$.instance").isEqualTo(path);
	}
}

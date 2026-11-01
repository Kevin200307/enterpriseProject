package lk.ac.mrt.swiftdrop.gateway.health;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayHealthEndpointTests {

	private static final DisposableServer HEALTHY_SERVICE = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes
					.get("/actuator/health", (request, response) -> response.status(200).send())
					.get("/api/v1/tracking/**", (request, response) -> response
							.header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5174")
							.header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
							.status(200)
							.send()))
			.bindNow();

	@LocalServerPort
	private int gatewayPort;

	private WebTestClient client;

	@DynamicPropertySource
	static void serviceProperties(DynamicPropertyRegistry registry) {
		String serviceUrl = "http://127.0.0.1:" + HEALTHY_SERVICE.port();
		registry.add("gateway.services.identity", () -> serviceUrl);
		registry.add("gateway.services.parcel", () -> serviceUrl);
		registry.add("gateway.services.pricing", () -> serviceUrl);
		registry.add("gateway.services.dispatch", () -> serviceUrl);
		registry.add("gateway.services.merchant", () -> serviceUrl);
		registry.add("gateway.services.tracking", () -> serviceUrl);
		registry.add("gateway.services.notification", () -> serviceUrl);
		registry.add("gateway.services.ai-assistant", () -> serviceUrl);
		registry.add("gateway.browser.origins", () -> "http://localhost:5174");
		registry.add("spring.data.redis.host", () -> "127.0.0.1");
		registry.add("spring.data.redis.port", () -> "1");
	}

	@BeforeEach
	void createClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + gatewayPort)
				.responseTimeout(Duration.ofSeconds(5))
				.build();
	}

	@AfterAll
	static void stopService() {
		HEALTHY_SERVICE.disposeNow();
	}

	@Test
	void reportsGatewayRedisAndEveryDownstreamComponent() {
		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.status").isEqualTo("DEGRADED")
				.jsonPath("$.components.redis.status").isEqualTo("DEGRADED")
				.jsonPath("$.components.downstream.components.identity.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.parcel.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.pricing.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.dispatch.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.merchant.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.tracking.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.notification.status").isEqualTo("UP")
				.jsonPath("$.components.downstream.components.aiAssistant.status").isEqualTo("UP");
	}

	@Test
	void deduplicatesCorsHeadersFromProxiedResponses() {
		client.get()
				.uri("/api/v1/tracking/SD-00000001")
				.header(HttpHeaders.ORIGIN, "http://localhost:5174")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
						"http://localhost:5174")
				.expectHeader().valueEquals(
						HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
						"true");
	}
}

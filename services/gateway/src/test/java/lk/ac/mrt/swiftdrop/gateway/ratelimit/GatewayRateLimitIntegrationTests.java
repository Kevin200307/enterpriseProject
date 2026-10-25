package lk.ac.mrt.swiftdrop.gateway.ratelimit;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"gateway.services.identity=http://127.0.0.1:1",
				"spring.cloud.gateway.server.webflux.httpclient.connect-timeout=100",
				"spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms",
				"resilience4j.timelimiter.configs.default.timeout-duration=500ms"
		})
class GatewayRateLimitIntegrationTests {

	@Container
	private static final GenericContainer<?> REDIS = new GenericContainer<>(
			DockerImageName.parse("redis:7-alpine"))
			.withExposedPorts(6379);

	@LocalServerPort
	private int gatewayPort;

	private WebTestClient client;

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", REDIS::getHost);
		registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
		registry.add("spring.data.redis.password", () -> "");
	}

	@BeforeEach
	void createClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + gatewayPort)
				.responseTimeout(Duration.ofSeconds(5))
				.build();
	}

	@Test
	void sixthLoginRequestWithinOneMinuteIsRejected() {
		for (int attempt = 1; attempt <= 5; attempt++) {
			client.post()
					.uri("/api/v1/auth/login")
					.exchange()
					.expectStatus().isEqualTo(503);
		}

		client.post()
				.uri("/api/v1/auth/login")
				.header("X-Request-Id", "limited-login")
				.exchange()
				.expectStatus().isEqualTo(429)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectHeader().valueEquals("Retry-After", "60")
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("RATE_LIMITED")
				.jsonPath("$.traceId").isEqualTo("limited-login")
				.jsonPath("$.instance").isEqualTo("/api/v1/auth/login");
	}
}

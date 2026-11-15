package lk.ac.mrt.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayOpenApiIntegrationTests {

	private static final List<String> SERVICES = List.of(
			"identity", "parcel", "pricing", "dispatch",
			"merchant", "tracking", "notification", "assistant");

	private static final DisposableServer OPENAPI_UPSTREAM = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes.get(
					"/v3/api-docs",
					(request, response) -> response
							.header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
							.sendString(reactor.core.publisher.Mono.just(
									"{\"openapi\":\"3.1.0\",\"path\":\"" + request.uri() + "\"}"))))
			.bindNow();

	@LocalServerPort
	private int gatewayPort;

	private WebTestClient client;

	@DynamicPropertySource
	static void serviceProperties(DynamicPropertyRegistry registry) {
		String url = "http://127.0.0.1:" + OPENAPI_UPSTREAM.port();
		registry.add("gateway.services.identity", () -> url);
		registry.add("gateway.services.parcel", () -> url);
		registry.add("gateway.services.pricing", () -> url);
		registry.add("gateway.services.dispatch", () -> url);
		registry.add("gateway.services.merchant", () -> url);
		registry.add("gateway.services.tracking", () -> url);
		registry.add("gateway.services.notification", () -> url);
		registry.add("gateway.services.ai-assistant", () -> url);
	}

	@BeforeEach
	void createClient() {
		client = WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + gatewayPort)
				.responseTimeout(Duration.ofSeconds(5))
				.build();
	}

	@AfterAll
	static void stopUpstream() {
		OPENAPI_UPSTREAM.disposeNow();
	}

	@Test
	void proxiesEveryServiceDocumentWithoutExposingInternalHostnames() {
		for (String service : SERVICES) {
			client.get()
					.uri("/docs/services/{service}/v3/api-docs", service)
					.exchange()
					.expectStatus().isOk()
					.expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
					.expectBody()
					.jsonPath("$.openapi").isEqualTo("3.1.0")
					.jsonPath("$.path").isEqualTo("/v3/api-docs");
		}
	}

	@Test
	void servesSwaggerUiWithRelativeGatewayDocumentUrls() {
		client.get()
				.uri("/docs")
				.exchange()
				.expectStatus().is3xxRedirection()
				.expectHeader().value("Location", location ->
						assertThat(location).doesNotContain("service:8080"));

		client.get()
				.uri("/swagger-ui/swagger-initializer.js")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class)
				.value(script -> {
					assertThat(script).contains("/v3/api-docs/swagger-config");
					assertThat(script).doesNotContain("identity-service:8080");
				});

		client.get()
				.uri("/v3/api-docs/swagger-config")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.urls[*].url").value(urls -> {
					List<?> actualUrls = (List<?>) urls;
					assertThat(actualUrls.stream().map(Object::toString).toList())
						.containsExactlyInAnyOrder(
								"/docs/services/identity/v3/api-docs",
								"/docs/services/parcel/v3/api-docs",
								"/docs/services/pricing/v3/api-docs",
								"/docs/services/dispatch/v3/api-docs",
								"/docs/services/merchant/v3/api-docs",
								"/docs/services/tracking/v3/api-docs",
								"/docs/services/notification/v3/api-docs",
								"/docs/services/assistant/v3/api-docs");
				})
				.jsonPath("$").value(configuration ->
						assertThat(configuration.toString()).doesNotContain("-service:8080"));
	}
}

package lk.ac.mrt.swiftdrop.gateway.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class DashboardIntegrationTests {

	private enum Behavior {
		SUCCESS,
		FAIL,
		TIMEOUT
	}

	private static final Map<String, AtomicReference<Behavior>> BEHAVIORS = Map.of(
			"/api/v1/parcels/summary", new AtomicReference<>(Behavior.SUCCESS),
			"/api/v1/dispatch/summary", new AtomicReference<>(Behavior.SUCCESS),
			"/api/v1/merchants/summary", new AtomicReference<>(Behavior.SUCCESS));

	private static final Map<String, String> AUTHORIZATION_HEADERS = new ConcurrentHashMap<>();
	private static final Map<String, String> REQUEST_ID_HEADERS = new ConcurrentHashMap<>();

	private static final DisposableServer UPSTREAM = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> BEHAVIORS.keySet().forEach(path -> routes.get(path, (request, response) -> {
				AUTHORIZATION_HEADERS.put(path, request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
				REQUEST_ID_HEADERS.put(path, request.requestHeaders().get("X-Request-Id"));

				return switch (BEHAVIORS.get(path).get()) {
					case SUCCESS -> response
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.sendString(Mono.just("{\"source\":\"" + path + "\"}"))
							.then();
					case FAIL -> response.status(HttpStatus.SERVICE_UNAVAILABLE.value()).send();
					case TIMEOUT -> response
							.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
							.sendString(Mono.delay(Duration.ofSeconds(1)).thenReturn("{\"late\":true}"))
							.then();
				};
			})))
			.bindNow();

	@DynamicPropertySource
	static void dashboardProperties(DynamicPropertyRegistry registry) {
		String baseUrl = "http://127.0.0.1:" + UPSTREAM.port();
		registry.add("gateway.services.parcel", () -> baseUrl);
		registry.add("gateway.services.dispatch", () -> baseUrl);
		registry.add("gateway.services.merchant", () -> baseUrl);
		registry.add("gateway.dashboard.timeout", () -> "300ms");
	}

	@AfterAll
	static void stopUpstream() {
		UPSTREAM.disposeNow();
	}

	@Autowired
	private ApplicationContext applicationContext;

	private WebTestClient client;

	@BeforeEach
	void setUp() {
		BEHAVIORS.values().forEach(behavior -> behavior.set(Behavior.SUCCESS));
		AUTHORIZATION_HEADERS.clear();
		REQUEST_ID_HEADERS.clear();
		client = WebTestClient.bindToApplicationContext(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void returnsAllSectionsAndForwardsRequestContext() {
		authenticatedRequest("dashboard-success")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.parcels.available").isEqualTo(true)
				.jsonPath("$.dispatch.available").isEqualTo(true)
				.jsonPath("$.merchants.available").isEqualTo(true)
				.jsonPath("$.parcels.data.source").isEqualTo("/api/v1/parcels/summary")
				.jsonPath("$.dispatch.data.source").isEqualTo("/api/v1/dispatch/summary")
				.jsonPath("$.merchants.data.source").isEqualTo("/api/v1/merchants/summary");

		assertThat(AUTHORIZATION_HEADERS.values())
				.containsOnly("Bearer token");
		assertThat(REQUEST_ID_HEADERS.values())
				.containsOnly("dashboard-success");
	}

	@Test
	void returnsPartialResultWhenOneServiceFails() {
		BEHAVIORS.get("/api/v1/dispatch/summary").set(Behavior.FAIL);

		authenticatedRequest("dashboard-failure")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.parcels.available").isEqualTo(true)
				.jsonPath("$.dispatch.available").isEqualTo(false)
				.jsonPath("$.dispatch.data").doesNotExist()
				.jsonPath("$.merchants.available").isEqualTo(true);
	}

	@Test
	void timesOutOnlyTheSlowSection() {
		BEHAVIORS.get("/api/v1/merchants/summary").set(Behavior.TIMEOUT);

		authenticatedRequest("dashboard-timeout")
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.parcels.available").isEqualTo(true)
				.jsonPath("$.dispatch.available").isEqualTo(true)
				.jsonPath("$.merchants.available").isEqualTo(false)
				.jsonPath("$.merchants.data").doesNotExist();
	}

	@Test
	void rejectsAnonymousRequests() {
		client.get()
				.uri("/api/v1/dashboard")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED");
	}

	private WebTestClient.ResponseSpec authenticatedRequest(String requestId) {
		return client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("20f4909a-9dc7-4fc0-a850-2cbb8641cd1d")))
				.get()
				.uri("/api/v1/dashboard")
				.header("X-Request-Id", requestId)
				.exchange();
	}
}

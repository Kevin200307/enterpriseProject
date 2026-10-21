package lk.ac.mrt.swiftdrop.gateway.config;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.MOCK,
		properties = {
				"gateway.services.identity=http://127.0.0.1:1",
				"gateway.services.parcel=http://127.0.0.1:1",
				"gateway.services.tracking=http://127.0.0.1:1",
				"spring.cloud.gateway.server.webflux.httpclient.connect-timeout=100",
				"spring.cloud.gateway.server.webflux.httpclient.response-timeout=500ms",
				"resilience4j.timelimiter.configs.default.timeout-duration=500ms"
		})
class SecurityConfigTests {

	@Autowired
	private ApplicationContext applicationContext;

	@Autowired
	private ServerAccessDeniedHandler accessDeniedHandler;

	private WebTestClient client;

	@BeforeEach
	void createClient() {
		client = WebTestClient.bindToApplicationContext(applicationContext)
				.apply(springSecurity())
				.build();
	}

	@Test
	void protectedRouteRejectsMissingBearerTokenWithProblemResponse() {
		client.get()
				.uri("/api/v1/parcels/parcel-123")
				.header("X-Request-Id", "security-request")
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED")
				.jsonPath("$.traceId").isEqualTo("security-request")
				.jsonPath("$.instance").isEqualTo("/api/v1/parcels/parcel-123");
	}

	@Test
	void protectedRouteRejectsInvalidBearerTokenWithoutLeakingIt() {
		client.get()
				.uri("/api/v1/parcels/parcel-123")
				.headers(headers -> headers.setBearerAuth("not-a-jwt"))
				.exchange()
				.expectStatus().isUnauthorized()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UNAUTHENTICATED")
				.jsonPath("$.detail").value(detail ->
						org.assertj.core.api.Assertions.assertThat(detail.toString())
								.doesNotContain("not-a-jwt"));
	}

	@Test
	void validJwtCanReachProtectedRoute() {
		client.mutateWith(mockJwt().jwt(jwt -> jwt.subject("user-123")))
				.get()
				.uri("/api/v1/parcels/parcel-123")
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UPSTREAM_UNAVAILABLE");
	}

	@Test
	void authAndExactPublicTrackingGetAreAnonymous() {
		assertAnonymousRequestReachesUpstream(HttpMethod.POST, "/api/v1/auth/login");
		assertAnonymousRequestReachesUpstream(HttpMethod.GET, "/api/v1/tracking/SD-123");
	}

	@Test
	void broaderTrackingSurfaceRemainsProtected() {
		client.get()
				.uri("/api/v1/tracking/SD-123/events")
				.exchange()
				.expectStatus().isUnauthorized();

		client.post()
				.uri("/api/v1/tracking/SD-123")
				.exchange()
				.expectStatus().isUnauthorized();
	}

	@Test
	void documentationIsPublic() {
		client.get()
				.uri("/v3/api-docs")
				.exchange()
				.expectStatus().isOk();
	}

	@Test
	void exactHealthEndpointIsPublic() {
		client.get()
				.uri("/actuator/health")
				.exchange()
				.expectStatus().value(status ->
						org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
	}

	@Test
	void accessDeniedHandlerWritesProblemResponse() {
		ServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/admin").build());
		exchange.getAttributes().put(
				lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter.REQUEST_ID_ATTRIBUTE,
				"denied-request");

		accessDeniedHandler.handle(exchange, new AccessDeniedException("sensitive reason")).block();

		org.assertj.core.api.Assertions.assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(403);
		org.assertj.core.api.Assertions.assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		String body = ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block();
		org.assertj.core.api.Assertions.assertThat(body)
				.contains("\"errorCode\":\"ACCESS_DENIED\"")
				.contains("\"traceId\":\"denied-request\"")
				.doesNotContain("sensitive reason");
	}

	private void assertAnonymousRequestReachesUpstream(HttpMethod method, String path) {
		client.method(method)
				.uri(path)
				.exchange()
				.expectStatus().isEqualTo(503)
				.expectBody()
				.jsonPath("$.errorCode").isEqualTo("UPSTREAM_UNAVAILABLE");
	}
}

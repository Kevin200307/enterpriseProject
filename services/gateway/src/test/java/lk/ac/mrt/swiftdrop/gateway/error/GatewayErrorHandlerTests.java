package lk.ac.mrt.swiftdrop.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

class GatewayErrorHandlerTests {

	private final GatewayErrorHandler handler = new GatewayErrorHandler(
			new GatewayProblemWriter(JsonMapper.builder().build()));

	@Test
	void normalizesRouteNotFound() {
		assertProblem(
				new ResponseStatusException(HttpStatus.NOT_FOUND, "internal route detail"),
				404,
				"RESOURCE_NOT_FOUND");
	}

	@Test
	void normalizesAuthenticationAuthorizationAndRateLimitFailures() {
		assertProblem(new ResponseStatusException(HttpStatus.UNAUTHORIZED), 401, "UNAUTHENTICATED");
		assertProblem(new ResponseStatusException(HttpStatus.FORBIDDEN), 403, "ACCESS_DENIED");

		MockServerWebExchange exchange = handle(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS));
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("60");
		assertThat(problem(exchange).read("$.errorCode", String.class)).isEqualTo("RATE_LIMITED");
	}

	@Test
	void normalizesTimeoutCircuitAndConnectionFailuresWithoutLeakingDetails() {
		assertProblem(
				new RuntimeException("database-password=secret", new TimeoutException("private host")),
				503,
				"UPSTREAM_UNAVAILABLE");
		assertProblem(new ConnectException("10.0.0.5:8080 refused"), 503, "UPSTREAM_UNAVAILABLE");
	}

	@Test
	void normalizesUnexpectedFailure() {
		assertProblem(new IllegalStateException("redis-password=secret"), 500, "INTERNAL_ERROR");
	}

	@Test
	void doesNotReplaceCommittedDownstreamProblemResponse() {
		MockServerWebExchange exchange = exchange();
		exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		StepVerifier.create(exchange.getResponse().setComplete()).verifyComplete();
		RuntimeException downstreamFailure = new RuntimeException("after commit");

		StepVerifier.create(handler.handle(exchange, downstreamFailure))
				.expectErrorMatches(error -> error == downstreamFailure)
				.verify();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private void assertProblem(Throwable exception, int expectedStatus, String expectedCode) {
		MockServerWebExchange exchange = handle(exception);
		DocumentContext problem = problem(exchange);
		assertThat(exchange.getResponse().getStatusCode().value()).isEqualTo(expectedStatus);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(problem.read("$.errorCode", String.class)).isEqualTo(expectedCode);
		assertThat(problem.read("$.traceId", String.class)).isEqualTo("error-request");
		assertThat(exchange.getResponse().getBodyAsString().block())
				.doesNotContain("database-password", "redis-password", "private host", "10.0.0.5");
	}

	private MockServerWebExchange handle(Throwable exception) {
		MockServerWebExchange exchange = exchange();
		StepVerifier.create(handler.handle(exchange, exception)).verifyComplete();
		return exchange;
	}

	private MockServerWebExchange exchange() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/missing").build());
		exchange.getAttributes().put(
				lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter.REQUEST_ID_ATTRIBUTE,
				"error-request");
		return exchange;
	}

	private DocumentContext problem(MockServerWebExchange exchange) {
		return JsonPath.parse(exchange.getResponse().getBodyAsString().block());
	}
}

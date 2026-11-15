package lk.ac.mrt.swiftdrop.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayProblemWriterTests {

	private final GatewayProblemWriter writer = new GatewayProblemWriter(JsonMapper.builder().build());

	@Test
	void writesTheSharedProblemContract() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/parcels").build());

		StepVerifier.create(writer.write(
				exchange,
				ErrorCode.UPSTREAM_UNAVAILABLE,
				"The parcel service is temporarily unavailable",
				"request-123"))
				.verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

		DocumentContext problem = JsonPath.parse(exchange.getResponse().getBodyAsString().block());
		assertThat(problem.read("$.type", String.class))
				.isEqualTo("urn:swiftdrop:error:upstream-unavailable");
		assertThat(problem.read("$.title", String.class)).isEqualTo("Upstream service unavailable");
		assertThat(problem.read("$.status", Integer.class)).isEqualTo(503);
		assertThat(problem.read("$.detail", String.class))
				.isEqualTo("The parcel service is temporarily unavailable");
		assertThat(problem.read("$.instance", String.class)).isEqualTo("/api/v1/parcels");
		assertThat(problem.read("$.errorCode", String.class)).isEqualTo("UPSTREAM_UNAVAILABLE");
		assertThat(problem.read("$.traceId", String.class)).isEqualTo("request-123");
		assertThatCodeIsAnInstant(problem.read("$.timestamp", String.class));
	}

	@Test
	void excludesTheQueryStringAndInternalFailureDetails() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/parcels?token=secret-value").build());

		StepVerifier.create(writer.write(
				exchange,
				ErrorCode.INTERNAL_ERROR,
				"The gateway could not process the request",
				null))
				.verifyComplete();

		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(body)
				.doesNotContain("secret-value")
				.doesNotContain("token=")
				.doesNotContain("Exception")
				.doesNotContain("stackTrace")
				.doesNotContain("java.");

		DocumentContext problem = JsonPath.parse(body);
		assertThat(problem.read("$.instance", String.class)).isEqualTo("/api/v1/parcels");
		assertThat(problem.read("$.traceId", String.class)).isEqualTo("unavailable");
	}

	@Test
	void usesASafeProblemWhenSerializationFails() {
		ObjectMapper failingMapper = mock(ObjectMapper.class);
		when(failingMapper.writeValueAsBytes(any()))
				.thenThrow(new IllegalStateException("database-password=do-not-expose"));
		GatewayProblemWriter failingWriter = new GatewayProblemWriter(failingMapper);
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get("/api/v1/parcels").build());

		StepVerifier.create(failingWriter.write(
				exchange,
				ErrorCode.UPSTREAM_UNAVAILABLE,
				"The parcel service is temporarily unavailable",
				"request-123"))
				.verifyComplete();

		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(body)
				.contains("\"errorCode\":\"INTERNAL_ERROR\"")
				.doesNotContain("database-password")
				.doesNotContain("do-not-expose")
				.doesNotContain("IllegalStateException");
	}

	private void assertThatCodeIsAnInstant(String value) {
		assertThat(Instant.parse(value)).isNotNull();
	}
}

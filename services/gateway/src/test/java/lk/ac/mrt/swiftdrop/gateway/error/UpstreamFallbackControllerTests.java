package lk.ac.mrt.swiftdrop.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class UpstreamFallbackControllerTests {

	private final UpstreamFallbackController controller = new UpstreamFallbackController(
			new GatewayProblemWriter(JsonMapper.builder().build()));

	@Test
	void returnsASafeCorrelatedUpstreamProblem() {
		MockServerWebExchange exchange = MockServerWebExchange.from(
				MockServerHttpRequest.get(UpstreamFallbackController.FALLBACK_PATH));
		exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-456");
		exchange.getAttributes().put(
				RequestIdFilter.ORIGINAL_REQUEST_PATH_ATTRIBUTE,
				"/api/v1/parcels/parcel-123");

		StepVerifier.create(controller.upstreamUnavailable(exchange)).verifyComplete();

		assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(exchange.getResponse().getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		String body = exchange.getResponse().getBodyAsString().block();
		assertThat(JsonPath.read(body, "$.errorCode").toString()).isEqualTo("UPSTREAM_UNAVAILABLE");
		assertThat(JsonPath.read(body, "$.traceId").toString()).isEqualTo("request-456");
		assertThat(JsonPath.read(body, "$.instance").toString())
				.isEqualTo("/api/v1/parcels/parcel-123");
		assertThat(body).doesNotContain("Exception").doesNotContain("java.");
	}
}

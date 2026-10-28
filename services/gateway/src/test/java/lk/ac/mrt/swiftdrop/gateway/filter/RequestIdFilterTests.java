package lk.ac.mrt.swiftdrop.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

class RequestIdFilterTests {

	private final RequestIdFilter filter = new RequestIdFilter();

	@Test
	void preservesOneValidRequestIdEverywhere() {
		MockServerWebExchange exchange = exchangeWithRequestIds("client-request_123:abc");
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return filteredExchange.getResponse().setComplete();
		})).verifyComplete();

		assertThat(RequestIdFilter.requestId(forwarded.get())).isEqualTo("client-request_123:abc");
		assertThat(forwarded.get().getRequest().getHeaders().get(RequestIdFilter.REQUEST_ID_HEADER))
				.containsExactly("client-request_123:abc");
		assertThat(exchange.getResponse().getHeaders().get(RequestIdFilter.REQUEST_ID_HEADER))
				.containsExactly("client-request_123:abc");
	}

	@Test
	void generatesAUuidWhenTheHeaderIsAbsent() {
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/parcels"));
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return filteredExchange.getResponse().setComplete();
		})).verifyComplete();

		String generated = RequestIdFilter.requestId(forwarded.get());
		assertThatCodeIsAUuid(generated);
		assertThat(forwarded.get().getRequest().getHeaders().get(RequestIdFilter.REQUEST_ID_HEADER))
				.containsExactly(generated);
		assertThat(exchange.getResponse().getHeaders().get(RequestIdFilter.REQUEST_ID_HEADER))
				.containsExactly(generated);
	}

	@Test
	void replacesAnInvalidRequestId() {
		String invalid = "contains spaces and ? invalid characters";
		MockServerWebExchange exchange = exchangeWithRequestIds(invalid);
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		String generated = RequestIdFilter.requestId(forwarded.get());
		assertThatCodeIsAUuid(generated);
		assertThat(generated).isNotEqualTo(invalid);
	}

	@Test
	void replacesDuplicateRequestIdHeadersWithOneCanonicalValue() {
		MockServerWebExchange exchange = exchangeWithRequestIds("first-id", "second-id");
		AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

		StepVerifier.create(filter.filter(exchange, filteredExchange -> {
			forwarded.set(filteredExchange);
			return Mono.empty();
		})).verifyComplete();

		String generated = RequestIdFilter.requestId(forwarded.get());
		assertThatCodeIsAUuid(generated);
		assertThat(forwarded.get().getRequest().getHeaders().get(RequestIdFilter.REQUEST_ID_HEADER))
				.isEqualTo(List.of(generated));
	}

	@Test
	void runsBeforeAllOrdinaryFilters() {
		assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
	}

	private MockServerWebExchange exchangeWithRequestIds(String... requestIds) {
		return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/parcels")
				.header(RequestIdFilter.REQUEST_ID_HEADER, requestIds));
	}

	private void assertThatCodeIsAUuid(String value) {
		assertThat(UUID.fromString(value)).isNotNull();
	}
}

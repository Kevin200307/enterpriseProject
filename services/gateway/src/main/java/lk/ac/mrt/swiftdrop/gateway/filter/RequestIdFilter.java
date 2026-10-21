package lk.ac.mrt.swiftdrop.gateway.filter;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import reactor.core.publisher.Mono;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Establishes one trusted correlation ID before any other gateway filter runs.
 */
@Component
public final class RequestIdFilter implements GlobalFilter, Ordered {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String REQUEST_ID_ATTRIBUTE = RequestIdFilter.class.getName() + ".requestId";
	public static final String ORIGINAL_REQUEST_PATH_ATTRIBUTE =
			RequestIdFilter.class.getName() + ".originalRequestPath";

	private static final int MAX_REQUEST_ID_LENGTH = 128;
	private static final Pattern VALID_REQUEST_ID =
			Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:-]{0," + (MAX_REQUEST_ID_LENGTH - 1) + "}");

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String requestId = resolveRequestId(exchange);
		exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
		exchange.getAttributes().put(
				ORIGINAL_REQUEST_PATH_ATTRIBUTE,
				exchange.getRequest().getPath().value());

		ServerHttpRequest canonicalRequest = exchange.getRequest().mutate()
				.headers(headers -> headers.set(REQUEST_ID_HEADER, requestId))
				.build();

		exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
		exchange.getResponse().beforeCommit(() -> {
			exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
			return Mono.empty();
		});

		return chain.filter(exchange.mutate().request(canonicalRequest).build());
	}

	@Override
	public int getOrder() {
		return Ordered.HIGHEST_PRECEDENCE;
	}

	/**
	 * Returns the canonical request ID after this filter has run.
	 */
	public static String requestId(ServerWebExchange exchange) {
		Object value = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
		return value instanceof String requestId ? requestId : null;
	}

	private String resolveRequestId(ServerWebExchange exchange) {
		List<String> suppliedValues = exchange.getRequest().getHeaders().get(REQUEST_ID_HEADER);
		if (suppliedValues != null && suppliedValues.size() == 1 && isValid(suppliedValues.getFirst())) {
			return suppliedValues.getFirst();
		}
		return UUID.randomUUID().toString();
	}

	private boolean isValid(String requestId) {
		return requestId != null
				&& requestId.length() <= MAX_REQUEST_ID_LENGTH
				&& VALID_REQUEST_ID.matcher(requestId).matches();
	}
}

package lk.ac.mrt.swiftdrop.gateway.filter;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

/**
 * Writes one safe, structured access event when gateway request processing ends.
 */
@Component
public final class AccessLogFilter implements WebFilter, Ordered {

	private static final Logger LOGGER = LoggerFactory.getLogger(AccessLogFilter.class);
	private static final String ANONYMOUS_USER = "anonymous";
	private static final String UNMATCHED_ROUTE = "unmatched";
	private static final String UNKNOWN_REQUEST_ID = "unknown";

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		long startedAt = System.nanoTime();
		return authenticatedUserId(exchange)
				.flatMap(userId -> chain.filter(exchange)
						.doFinally(signal -> logCompletion(exchange, userId, startedAt, signal)));
	}

	@Override
	public int getOrder() {
		return Ordered.LOWEST_PRECEDENCE - 101;
	}

	private Mono<String> authenticatedUserId(ServerWebExchange exchange) {
		return exchange.getPrincipal()
				.ofType(JwtAuthenticationToken.class)
				.map(authentication -> authentication.getToken().getSubject())
				.filter(subject -> subject != null && !subject.isBlank())
				.defaultIfEmpty(ANONYMOUS_USER);
	}

	private void logCompletion(
			ServerWebExchange exchange,
			String userId,
			long startedAt,
			SignalType signal) {

		LOGGER.atInfo()
				.addKeyValue("event", "gateway_access")
				.addKeyValue("method", exchange.getRequest().getMethod().name())
				.addKeyValue("path", exchange.getRequest().getPath().value())
				.addKeyValue("status", status(exchange, signal))
				.addKeyValue("durationMs", Duration.ofNanos(System.nanoTime() - startedAt).toMillis())
				.addKeyValue("routeId", routeId(exchange))
				.addKeyValue("userId", userId)
				.addKeyValue("requestId", requestId(exchange))
				.log("gateway request completed");
	}

	private int status(ServerWebExchange exchange, SignalType signal) {
		if (exchange.getResponse().getStatusCode() != null) {
			return exchange.getResponse().getStatusCode().value();
		}
		if (signal == SignalType.ON_ERROR) {
			return HttpStatus.INTERNAL_SERVER_ERROR.value();
		}
		if (signal == SignalType.CANCEL) {
			return 499;
		}
		return HttpStatus.OK.value();
	}

	private String routeId(ServerWebExchange exchange) {
		Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
		return route == null ? UNMATCHED_ROUTE : route.getId();
	}

	private String requestId(ServerWebExchange exchange) {
		String requestId = RequestIdFilter.requestId(exchange);
		return requestId == null ? UNKNOWN_REQUEST_ID : requestId;
	}
}

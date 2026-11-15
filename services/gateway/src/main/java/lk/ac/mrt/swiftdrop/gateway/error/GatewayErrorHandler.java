package lk.ac.mrt.swiftdrop.gateway.error;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import reactor.core.publisher.Mono;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

/** Final non-blocking normalization boundary for exceptions owned by the gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class GatewayErrorHandler implements WebExceptionHandler {

	private static final String RETRY_AFTER_SECONDS = "60";

	private final GatewayProblemWriter problemWriter;

	public GatewayErrorHandler(GatewayProblemWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
		if (exchange.getResponse().isCommitted()) {
			return Mono.error(exception);
		}

		Failure failure = classify(exception);
		if (failure.errorCode() == ErrorCode.RATE_LIMITED
				&& exchange.getResponse().getHeaders().getFirst("Retry-After") == null) {
			exchange.getResponse().getHeaders().set("Retry-After", RETRY_AFTER_SECONDS);
		}
		return problemWriter.write(
				exchange,
				failure.errorCode(),
				failure.safeDetail(),
				RequestIdFilter.requestId(exchange));
	}

	private Failure classify(Throwable exception) {
		if (hasCause(exception, AuthenticationException.class)) {
			return new Failure(
					ErrorCode.UNAUTHENTICATED,
					"A valid bearer token is required to access this resource");
		}
		if (hasCause(exception, AccessDeniedException.class)) {
			return new Failure(
					ErrorCode.ACCESS_DENIED,
					"You do not have permission to access this resource");
		}
		if (hasCause(exception, TimeoutException.class)
				|| hasCause(exception, ConnectException.class)
				|| hasCause(exception, CallNotPermittedException.class)) {
			return new Failure(
					ErrorCode.UPSTREAM_UNAVAILABLE,
					"A required upstream service is temporarily unavailable");
		}
		if (exception instanceof ResponseStatusException statusException) {
			return fromStatus(statusException.getStatusCode().value());
		}
		return new Failure(
				ErrorCode.INTERNAL_ERROR,
				"The gateway could not process the request");
	}

	private Failure fromStatus(int status) {
		return switch (status) {
			case 401 -> new Failure(
					ErrorCode.UNAUTHENTICATED,
					"A valid bearer token is required to access this resource");
			case 403 -> new Failure(
					ErrorCode.ACCESS_DENIED,
					"You do not have permission to access this resource");
			case 404 -> new Failure(
					ErrorCode.RESOURCE_NOT_FOUND,
					"The requested gateway resource was not found");
			case 429 -> new Failure(
					ErrorCode.RATE_LIMITED,
					"The request rate limit has been exceeded");
			case 502, 503, 504 -> new Failure(
					ErrorCode.UPSTREAM_UNAVAILABLE,
					"A required upstream service is temporarily unavailable");
			default -> status >= 500
					? new Failure(
							ErrorCode.INTERNAL_ERROR,
							"The gateway could not process the request")
					: new Failure(
							ErrorCode.MALFORMED_REQUEST,
							"The gateway could not accept the request");
		};
	}

	private boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
		Throwable current = exception;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private record Failure(ErrorCode errorCode, String safeDetail) {
	}
}

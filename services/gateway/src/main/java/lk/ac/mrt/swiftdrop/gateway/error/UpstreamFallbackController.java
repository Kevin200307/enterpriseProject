package lk.ac.mrt.swiftdrop.gateway.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.gateway.filter.RequestIdFilter;
import reactor.core.publisher.Mono;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/**
 * Internal target for circuit-breaker forwards. No exception detail from the
 * failed downstream call is reflected to the client.
 */
@RestController
final class UpstreamFallbackController {

	static final String FALLBACK_PATH = "/internal/gateway-fallback";
	private static final String SAFE_DETAIL = "The requested service is temporarily unavailable";

	private final GatewayProblemWriter problemWriter;

	UpstreamFallbackController(GatewayProblemWriter problemWriter) {
		this.problemWriter = problemWriter;
	}

	@RequestMapping(FALLBACK_PATH)
	Mono<Void> upstreamUnavailable(ServerWebExchange exchange) {
		return problemWriter.write(
				exchange,
				ErrorCode.UPSTREAM_UNAVAILABLE,
				SAFE_DETAIL,
				RequestIdFilter.requestId(exchange));
	}
}

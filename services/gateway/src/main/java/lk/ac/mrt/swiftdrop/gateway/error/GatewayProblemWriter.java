package lk.ac.mrt.swiftdrop.gateway.error;

import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.DETAIL;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.ERROR_CODE;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.INSTANCE;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.STATUS;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.TIMESTAMP;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.TITLE;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.TRACE_ID;
import static lk.ac.mrt.swiftdrop.contracts.error.ProblemFields.TYPE;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * Writes gateway-owned failures using SwiftDrop's shared RFC 7807 contract.
 *
 * <p>Callers must supply a client-safe detail message, never an exception
 * message. This writer deliberately accepts no {@link Throwable}, which keeps
 * stack traces and internal failure data out of the response path.</p>
 */
@Component
public final class GatewayProblemWriter {

	private static final String UNKNOWN_TRACE_ID = "unavailable";
	private static final byte[] SERIALIZATION_FAILURE_BODY = ("""
			{"type":"urn:swiftdrop:error:internal-error","title":"Internal server error",\
			"status":500,"detail":"The gateway could not create an error response",\
			"instance":"","errorCode":"INTERNAL_ERROR","traceId":"unavailable",\
			"timestamp":"1970-01-01T00:00:00Z"}""").getBytes(StandardCharsets.UTF_8);

	private final ObjectMapper objectMapper;

	public GatewayProblemWriter(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * Writes one problem response, unless the reactive response has already been
	 * committed.
	 *
	 * @param exchange current server exchange
	 * @param errorCode stable shared error code
	 * @param safeDetail non-sensitive explanation suitable for the API client
	 * @param traceId correlation ID, or {@code null} when correlation has not yet
	 *                been established
	 */
	public Mono<Void> write(
			ServerWebExchange exchange,
			ErrorCode errorCode,
			String safeDetail,
			String traceId) {

		Objects.requireNonNull(exchange, "exchange must not be null");
		Objects.requireNonNull(errorCode, "errorCode must not be null");
		Objects.requireNonNull(safeDetail, "safeDetail must not be null");

		if (exchange.getResponse().isCommitted()) {
			return Mono.empty();
		}

		Map<String, Object> problem = new LinkedHashMap<>();
		problem.put(TYPE, errorCode.type().toString());
		problem.put(TITLE, errorCode.title());
		problem.put(STATUS, errorCode.httpStatus());
		problem.put(DETAIL, safeDetail);
		problem.put(INSTANCE, exchange.getRequest().getPath().value());
		problem.put(ERROR_CODE, errorCode.name());
		problem.put(TRACE_ID, usableTraceId(traceId));
		problem.put(TIMESTAMP, Instant.now().toString());

		byte[] body;
		try {
			body = objectMapper.writeValueAsBytes(problem);
		}
		catch (RuntimeException exception) {
			return writeBytes(exchange, ErrorCode.INTERNAL_ERROR.httpStatus(), SERIALIZATION_FAILURE_BODY);
		}

		return writeBytes(exchange, errorCode.httpStatus(), body);
	}

	private Mono<Void> writeBytes(ServerWebExchange exchange, int status, byte[] body) {
		exchange.getResponse().setStatusCode(HttpStatusCode.valueOf(status));
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		exchange.getResponse().getHeaders().setContentLength(body.length);
		return exchange.getResponse().writeWith(
				Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
	}

	private String usableTraceId(String traceId) {
		return traceId == null || traceId.isBlank() ? UNKNOWN_TRACE_ID : traceId;
	}
}

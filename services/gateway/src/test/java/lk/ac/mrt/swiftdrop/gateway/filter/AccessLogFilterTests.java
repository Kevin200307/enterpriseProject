package lk.ac.mrt.swiftdrop.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

class AccessLogFilterTests {

	private static final Logger LOGGER =
			(Logger) LoggerFactory.getLogger(AccessLogFilter.class);

	private final AccessLogFilter filter = new AccessLogFilter();
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void captureAccessEvents() {
		appender.start();
		LOGGER.addAppender(appender);
	}

	@AfterEach
	void stopCapturingAccessEvents() {
		LOGGER.detachAppender(appender);
		appender.stop();
	}

	@Test
	void logsOneStructuredCompletionEvent() {
		MockServerWebExchange exchange = exchange();
		exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "request-123");
		exchange.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, Route.async()
				.id("parcel-api")
				.uri(URI.create("http://parcel-service:8080"))
				.predicate(ignored -> true)
				.build());

		StepVerifier.create(filter.filter(exchange, current -> {
			current.getResponse().setStatusCode(HttpStatus.CREATED);
			return current.getResponse().setComplete();
		})).verifyComplete();

		assertThat(appender.list).hasSize(1);
		ILoggingEvent event = appender.list.getFirst();
		assertThat(event.getLevel()).isEqualTo(Level.INFO);
		assertThat(event.getFormattedMessage()).isEqualTo("gateway request completed");

		Map<String, Object> fields = fields(event);
		assertThat(fields).containsEntry("event", "gateway_access")
				.containsEntry("method", "POST")
				.containsEntry("path", "/api/v1/parcels")
				.containsEntry("status", 201)
				.containsEntry("routeId", "parcel-api")
				.containsEntry("userId", "anonymous")
				.containsEntry("requestId", "request-123");
		assertThat((Long) fields.get("durationMs")).isNotNegative();
	}

	@Test
	void neverLogsQueryCredentialsCookiesOrBodies() {
		MockServerWebExchange exchange = exchange();
		exchange.getAttributes().put(RequestIdFilter.REQUEST_ID_ATTRIBUTE, "safe-request-id");

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

		String completeEvent = appender.list.getFirst().toString()
				+ appender.list.getFirst().getKeyValuePairs();
		assertThat(completeEvent)
				.doesNotContain("query-secret")
				.doesNotContain("bearer-secret")
				.doesNotContain("cookie-secret")
				.doesNotContain("body-secret");
	}

	@Test
	void logsTheValidatedJwtSubjectAsTheUserId() {
		String userId = "3f01b670-7642-4b41-a4ec-12dd605c70c4";
		Jwt jwt = new Jwt(
				"token",
				Instant.now(),
				Instant.now().plusSeconds(60),
				Map.of("alg", "none"),
				Map.of("sub", userId));
		ServerWebExchange exchange = exchange().mutate()
				.principal(Mono.just(new JwtAuthenticationToken(jwt, List.of())))
				.build();

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.empty())).verifyComplete();

		assertThat(fields(appender.list.getFirst())).containsEntry("userId", userId);
	}

	@Test
	void runsAfterSecurityAndImmediatelyBeforeIdentityHeaderPropagation() {
		assertThat(filter.getOrder()).isEqualTo(Ordered.LOWEST_PRECEDENCE - 101);
		assertThat(filter.getOrder()).isLessThan(new IdentityHeaderFilter().getOrder());
	}

	@Test
	void logsOneEventWhenTheChainFails() {
		MockServerWebExchange exchange = exchange();

		StepVerifier.create(filter.filter(exchange, ignored -> Mono.error(new IllegalStateException("private"))))
				.expectError(IllegalStateException.class)
				.verify();

		assertThat(appender.list).hasSize(1);
		assertThat(fields(appender.list.getFirst()))
				.containsEntry("status", 500)
				.containsEntry("routeId", "unmatched");
		assertThat(appender.list.getFirst().toString()).doesNotContain("private");
	}

	private MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest.post(
					"/api/v1/parcels?token=query-secret")
				.header("Authorization", "Bearer bearer-secret")
				.cookie(new HttpCookie("session", "cookie-secret"))
				.body("body-secret"));
	}

	private Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}
}

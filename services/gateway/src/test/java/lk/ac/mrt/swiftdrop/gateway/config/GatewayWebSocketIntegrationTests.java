package lk.ac.mrt.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "spring.main.allow-bean-definition-overriding=true")
@Import(GatewayWebSocketIntegrationTests.TestSecurityConfiguration.class)
class GatewayWebSocketIntegrationTests {

	private static final AtomicReference<String> FORWARDED_AUTHORIZATION = new AtomicReference<>();
	private static final DisposableServer WEBSOCKET_UPSTREAM = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes
					.ws("/ws", (inbound, outbound) -> {
						FORWARDED_AUTHORIZATION.set(inbound.headers().get(HttpHeaders.AUTHORIZATION));
						return outbound.send(inbound.receive().retain());
					})
					.get("/ws/info", (request, response) -> response.sendString(Mono.just("sockjs-info"))))
			.bindNow();

	@LocalServerPort
	private int gatewayPort;

	@TestConfiguration(proxyBeanMethods = false)
	static class TestSecurityConfiguration {

		@Bean
		SecurityWebFilterChain gatewaySecurityWebFilterChain(ServerHttpSecurity http) {
			return http
					.csrf(ServerHttpSecurity.CsrfSpec::disable)
					.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
					.build();
		}
	}

	@DynamicPropertySource
	static void websocketProperties(DynamicPropertyRegistry registry) {
		registry.add(
				"TRACKING_WEBSOCKET_URL",
				() -> "ws://127.0.0.1:" + WEBSOCKET_UPSTREAM.port());
		registry.add(
				"gateway.services.tracking",
				() -> "http://127.0.0.1:" + WEBSOCKET_UPSTREAM.port());
	}

	@AfterAll
	static void stopUpstream() {
		WEBSOCKET_UPSTREAM.disposeNow();
	}

	@Test
	void proxiesAnUpgradedConnectionAndPreservesAuthorization() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth("gateway-websocket-token");
		AtomicReference<String> response = new AtomicReference<>();

		new ReactorNettyWebSocketClient().execute(
				URI.create("ws://127.0.0.1:" + gatewayPort + "/ws"),
				headers,
				session -> session.send(Mono.just(session.textMessage("tracking-update")))
						.thenMany(session.receive().take(1))
						.doOnNext(message -> response.set(message.getPayloadAsText()))
						.then())
				.block(Duration.ofSeconds(5));

		assertThat(response).hasValue("tracking-update");
		assertThat(FORWARDED_AUTHORIZATION).hasValue("Bearer gateway-websocket-token");
	}

	@Test
	void proxiesSockJsHttpNegotiationWithoutForcingAnUpgrade() {
		WebTestClient.bindToServer()
				.baseUrl("http://127.0.0.1:" + gatewayPort)
				.build()
				.get()
				.uri("/ws/info")
				.exchange()
				.expectStatus().isOk()
				.expectBody(String.class).isEqualTo("sockjs-info");
	}
}

package lk.ac.mrt.swiftdrop.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest(
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = {
				"spring.main.allow-bean-definition-overriding=true",
				"spring.cloud.gateway.server.webflux.httpclient.response-timeout=100ms"
		})
@Import(GatewaySseIntegrationTests.TestSecurityConfiguration.class)
class GatewaySseIntegrationTests {

	private static final DisposableServer SSE_UPSTREAM = HttpServer.create()
			.host("127.0.0.1")
			.port(0)
			.route(routes -> routes.get(
					"/api/v1/tracking/public/SD-123/stream",
					(request, response) -> response
							.header("Content-Type", MediaType.TEXT_EVENT_STREAM_VALUE)
							.sendString(Flux.concat(
									Mono.just("data:first\n\n"),
									Mono.delay(Duration.ofMillis(250))
											.thenReturn("data:second\n\n")))))
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
	static void trackingServiceProperty(DynamicPropertyRegistry registry) {
		registry.add(
				"gateway.services.tracking",
				() -> "http://127.0.0.1:" + SSE_UPSTREAM.port());
	}

	@AfterAll
	static void stopUpstream() {
		SSE_UPSTREAM.disposeNow();
	}

	@Test
	void receivesMultipleEventsIncrementallyWithBufferingDisabled() {
		AtomicReference<HttpStatusCode> status = new AtomicReference<>();
		AtomicReference<String> contentType = new AtomicReference<>();
		AtomicReference<String> buffering = new AtomicReference<>();

		List<String> events = WebClient.create("http://127.0.0.1:" + gatewayPort)
				.get()
				.uri("/api/v1/tracking/public/SD-123/stream")
				.exchangeToFlux(response -> {
					status.set(response.statusCode());
					contentType.set(response.headers().asHttpHeaders().getFirst("Content-Type"));
					buffering.set(response.headers().asHttpHeaders().getFirst("X-Accel-Buffering"));
					return response.bodyToFlux(String.class);
				})
				.take(2)
				.collectList()
				.block(Duration.ofSeconds(5));

		assertThat(status.get().value()).isEqualTo(200);
		assertThat(contentType.get()).startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
		assertThat(buffering).hasValue("no");
		assertThat(events).hasSize(2);
		assertThat(String.join("", events)).contains("first", "second");
	}
}

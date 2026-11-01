package lk.ac.mrt.swiftdrop.gateway.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

import org.springframework.boot.health.contributor.Status;
import org.springframework.web.reactive.function.client.WebClient;

class DownstreamHealthIndicatorTests {

	private DisposableServer server;

	@AfterEach
	void stopServer() {
		if (server != null) {
			server.disposeNow();
		}
	}

	@Test
	void reportsUpForAHealthyService() {
		server = server((request, response) -> response.status(200).send());

		StepVerifier.create(indicator(server.port()).health())
				.assertNext(health -> assertThat(health.getStatus()).isEqualTo(Status.UP))
				.verifyComplete();
	}

	@Test
	void reportsDegradedForAnUnhealthyResponse() {
		server = server((request, response) -> response.status(503).send());

		StepVerifier.create(indicator(server.port()).health())
				.assertNext(health -> assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED"))
				.verifyComplete();
	}

	@Test
	void timesOutSlowServicesAsDegraded() {
		server = server((request, response) -> response.sendString(
				Mono.delay(DownstreamHealthIndicator.PROBE_TIMEOUT.plusSeconds(1)).thenReturn("late")));

		StepVerifier.create(indicator(server.port()).health())
				.assertNext(health -> assertThat(health.getStatus().getCode()).isEqualTo("DEGRADED"))
				.verifyComplete();
	}

	private DisposableServer server(java.util.function.BiFunction<
			reactor.netty.http.server.HttpServerRequest,
			reactor.netty.http.server.HttpServerResponse,
			org.reactivestreams.Publisher<Void>> handler) {
		return HttpServer.create()
				.host("127.0.0.1")
				.port(0)
				.route(routes -> routes.get("/actuator/health", handler))
				.bindNow();
	}

	private DownstreamHealthIndicator indicator(int port) {
		return new DownstreamHealthIndicator(
				WebClient.builder(),
				URI.create("http://127.0.0.1:" + port));
	}
}

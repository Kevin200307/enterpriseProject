package lk.ac.mrt.swiftdrop.gateway.health;

import java.net.URI;
import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Probes one downstream service without making its optional availability fatal
 * to the gateway itself.
 */
public final class DownstreamHealthIndicator implements ReactiveHealthIndicator {

	public static final String DEGRADED = "DEGRADED";
	static final Duration PROBE_TIMEOUT = Duration.ofMillis(750);

	private final WebClient client;

	public DownstreamHealthIndicator(WebClient.Builder webClientBuilder, URI serviceUri) {
		this.client = webClientBuilder
				.baseUrl(serviceUri.toString())
				.build();
	}

	@Override
	public Mono<Health> health() {
		return client.get()
				.uri("/actuator/health")
				.exchangeToMono(response -> response.statusCode().is2xxSuccessful()
						? Mono.just(Health.up().build())
						: Mono.just(degraded()))
				.timeout(PROBE_TIMEOUT)
				.onErrorReturn(degraded());
	}

	private Health degraded() {
		return Health.status(DEGRADED).build();
	}
}

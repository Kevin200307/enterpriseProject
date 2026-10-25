package lk.ac.mrt.swiftdrop.gateway.health;

import java.time.Duration;

import reactor.core.publisher.Mono;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

/**
 * Reports Redis degradation consistently with the gateway's fail-open rate
 * limiting policy.
 */
public final class RateLimitRedisHealthIndicator implements ReactiveHealthIndicator {

	private static final Duration PROBE_TIMEOUT = Duration.ofMillis(750);

	private final ReactiveRedisConnectionFactory connectionFactory;

	public RateLimitRedisHealthIndicator(ReactiveRedisConnectionFactory connectionFactory) {
		this.connectionFactory = connectionFactory;
	}

	@Override
	public Mono<Health> health() {
		return Mono.usingWhen(
					Mono.fromSupplier(connectionFactory::getReactiveConnection),
					this::ping,
					ReactiveRedisConnection::closeLater)
				.timeout(PROBE_TIMEOUT)
				.onErrorReturn(Health.status(DownstreamHealthIndicator.DEGRADED).build());
	}

	private Mono<Health> ping(ReactiveRedisConnection connection) {
		return connection.ping()
				.map(ignored -> Health.up().build());
	}
}

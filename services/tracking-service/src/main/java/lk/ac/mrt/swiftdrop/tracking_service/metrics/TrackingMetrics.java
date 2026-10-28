package lk.ac.mrt.swiftdrop.tracking_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Collection;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingSubscriptionInterceptor;
import lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingSseService;
import org.springframework.stereotype.Component;

/**
 * Micrometer metrics instrumentation for live tracking service.
 */
@Component
public class TrackingMetrics {

    private final Counter processedEventsCounter;
    private final Counter duplicateEventsCounter;
    private final Counter outOfOrderEventsCounter;
    private final Counter consumerErrorsCounter;
    private final Counter dlqEventsCounter;
    private final Counter redisFallbackCounter;

    public TrackingMetrics(
            MeterRegistry registry,
            TrackingSubscriptionInterceptor subscriptionInterceptor,
            TrackingSseService sseService) {

        this.processedEventsCounter = Counter.builder("tracking.events.processed")
                .description("Total number of successfully processed parcel tracking events")
                .register(registry);

        this.duplicateEventsCounter = Counter.builder("tracking.events.duplicate")
                .description("Total number of deduplicated/ignored redundant events")
                .register(registry);

        this.outOfOrderEventsCounter = Counter.builder("tracking.events.out_of_order")
                .description("Total number of out-of-order events handled without status regression")
                .register(registry);

        this.consumerErrorsCounter = Counter.builder("tracking.consumer.errors")
                .description("Total number of RabbitMQ listener consumer errors")
                .register(registry);

        this.dlqEventsCounter = Counter.builder("tracking.dlq.count")
                .description("Total number of poison/failed events routed to dead-letter queue")
                .register(registry);

        this.redisFallbackCounter = Counter.builder("tracking.redis.fallback")
                .description("Total number of times Redis outage degraded to direct upstream read")
                .register(registry);

        // Gauges for active WebSocket and SSE subscriptions
        Gauge.builder("tracking.websockets.active", subscriptionInterceptor,
                si -> si.getSessionSubscriptions().values().stream().mapToInt(Collection::size).sum())
                .description("Number of active STOMP/WebSocket parcel topic subscriptions")
                .register(registry);

        Gauge.builder("tracking.sse.active", sseService,
                ss -> ss.getActiveSinks().values().stream().mapToInt(Collection::size).sum())
                .description("Number of active SSE tracking streams")
                .register(registry);
    }

    public void incrementProcessed() {
        processedEventsCounter.increment();
    }

    public void incrementDuplicate() {
        duplicateEventsCounter.increment();
    }

    public void incrementOutOfOrder() {
        outOfOrderEventsCounter.increment();
    }

    public void incrementConsumerError() {
        consumerErrorsCounter.increment();
    }

    public void incrementDlq() {
        dlqEventsCounter.increment();
    }

    public void incrementRedisFallback() {
        redisFallbackCounter.increment();
    }

    public double getOutOfOrderCount() {
        return outOfOrderEventsCounter.count();
    }

    public double getProcessedCount() {
        return processedEventsCounter.count();
    }
}

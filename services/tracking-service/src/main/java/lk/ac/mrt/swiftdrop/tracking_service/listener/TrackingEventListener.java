package lk.ac.mrt.swiftdrop.tracking_service.listener;

import java.time.Duration;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.metrics.TrackingMetrics;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumer for parcel events with idempotency and DLQ support.
 * Deduplicates with {@code tracking:event:{eventId}} for 24h.
 * Deduplication and projection update are failure-safe: the event is only
 * permanently marked processed when projection update succeeds.
 */
@Slf4j
@Component
public class TrackingEventListener {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TrackingProjectionService projectionService;
    private final TrackingProperties properties;
    private final TrackingMetrics metrics;

    @Autowired
    public TrackingEventListener(
            ReactiveStringRedisTemplate redisTemplate,
            TrackingProjectionService projectionService,
            TrackingProperties properties,
            @Autowired(required = false) TrackingMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.projectionService = projectionService;
        this.properties = properties;
        this.metrics = metrics;
    }

    public TrackingEventListener(
            ReactiveStringRedisTemplate redisTemplate,
            TrackingProjectionService projectionService,
            TrackingProperties properties) {
        this(redisTemplate, projectionService, properties, null);
    }

    @RabbitListener(queues = Queues.TRACKING_PARCEL_STATUS_CHANGED)
    public void onParcelStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        processEvent(envelope.eventId(), envelope.payload().trackingNumber(),
                () -> projectionService.applyStatusChanged(envelope));
    }

    @RabbitListener(queues = Queues.TRACKING_PARCEL_STATUS_CHANGED_V2)
    public void onParcelStatusChangedV2(EventEnvelope<ParcelStatusChangedV2> envelope) {
        String trackingNumber = envelope.payload().trackingNumber();
        processEvent(envelope.eventId(), trackingNumber, () -> projectionService.applyStatusChangedV2(envelope));
    }

    @RabbitListener(queues = Queues.TRACKING_PARCEL_SNAPSHOT)
    public void onParcelTrackingSnapshot(EventEnvelope<ParcelTrackingSnapshotV1> envelope) {
        processEvent(envelope.eventId(), envelope.payload().trackingNumber(),
                () -> projectionService.applySnapshot(envelope));
    }

    private void processEvent(UUID eventId, String trackingNumber, Runnable projectionUpdate) {
        String dedupKey = "tracking:event:" + eventId;
        String processed = redisTemplate.opsForValue().get(dedupKey).block();
        if ("PROCESSED".equals(processed)) {
            log.info("Duplicate event ignored: eventId={}, trackingNumber={}", eventId, trackingNumber);
            if (metrics != null) {
                metrics.incrementDuplicate();
            }
            return;
        }

        try {
            projectionUpdate.run();

            int dedupHours = properties.cache() != null ? properties.cache().eventDedupTtlHours() : 24;
            redisTemplate.opsForValue().set(dedupKey, "PROCESSED", Duration.ofHours(dedupHours)).block();
            log.info("Processed parcel tracking event: eventId={}, trackingNumber={}",
                    eventId, trackingNumber);
            if (metrics != null) {
                metrics.incrementProcessed();
            }
        } catch (Exception ex) {
            log.error("Failed to process eventId={}, releasing dedup key for retry/DLQ", eventId, ex);
            if (metrics != null) {
                metrics.incrementConsumerError();
            }
            redisTemplate.delete(dedupKey).block();
            throw ex;
        }
    }
}

package lk.ac.mrt.swiftdrop.tracking_service.listener;

import java.time.Duration;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
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
@RequiredArgsConstructor
public class TrackingEventListener {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final TrackingProjectionService projectionService;
    private final TrackingProperties properties;

    @RabbitListener(queues = Queues.TRACKING_PARCEL_STATUS_CHANGED)
    public void onParcelStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        UUID eventId = envelope.eventId();
        String dedupKey = "tracking:event:" + eventId;

        // Idempotency check: if already processed, ack and drop
        String processed = redisTemplate.opsForValue().get(dedupKey).block();
        if ("PROCESSED".equals(processed)) {
            log.info("Duplicate event ignored: eventId={}, trackingNumber={}",
                    eventId, envelope.payload().trackingNumber());
            return;
        }

        try {
            // Update the tracking projection
            projectionService.applyStatusChanged(envelope);

            // Mark event as processed only after projection succeeds
            int dedupHours = properties.cache() != null ? properties.cache().eventDedupTtlHours() : 24;
            redisTemplate.opsForValue().set(dedupKey, "PROCESSED", Duration.ofHours(dedupHours)).block();
            log.info("Processed parcel status changed: eventId={}, trackingNumber={}, toStatus={}",
                    eventId, envelope.payload().trackingNumber(), envelope.payload().toStatus());
        } catch (Exception ex) {
            log.error("Failed to process eventId={}, releasing dedup key for retry/DLQ", eventId, ex);
            redisTemplate.delete(dedupKey).block();
            throw ex;
        }
    }
}

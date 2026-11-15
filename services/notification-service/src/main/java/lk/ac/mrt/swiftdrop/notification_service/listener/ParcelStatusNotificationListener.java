package lk.ac.mrt.swiftdrop.notification_service.listener;

import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParcelStatusNotificationListener {

    private final NotificationService notificationService;

    /**
     * Consumes parcel status changed events from RabbitMQ queue.
     * Queue: notification.parcel.status.changed.v1
     */
    @RabbitListener(queues = Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2)
    public void onParcelStatusChanged(EventEnvelope<ParcelStatusChangedV2> envelope) {
        log.info("Received parcel status changed event: eventId={}, trackingNumber={}, toStatus={}",
                envelope.eventId(),
                envelope.payload().trackingNumber(),
                envelope.payload().toStatus());

        try {
            notificationService.handleParcelStatusChanged(envelope);
        } catch (Exception e) {
            log.error("Error processing notification for eventId={}", envelope.eventId(), e);
            throw e; // Rethrow to trigger RabbitMQ retry and DLQ routing
        }
    }
}

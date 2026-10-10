package lk.ac.mrt.swiftdrop.notification_service.channel;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;

/**
 * Strategy interface for notification channels (e.g. Email, SMS, WhatsApp).
 * 
 * <p>Design Decision (Open-Closed Principle):
 * Core notification workflows depend exclusively on this interface.
 * Adding new delivery mediums (such as WhatsApp, Web Push) in the future
 * requires only creating a new {@link NotificationChannelHandler} implementation
 * without modifying {@link lk.ac.mrt.swiftdrop.notification_service.service.NotificationService}
 * or the event listener.
 */
public interface NotificationChannelHandler {

    /**
     * Determines whether this handler supports the specified channel enum.
     */
    boolean supports(NotificationChannel channel);

    /**
     * Returns the channel type supported by this handler.
     */
    NotificationChannel getChannel();

    /**
     * Dispatches the notification message to the recipient.
     */
    void send(NotificationRequest request);

    /**
     * Encapsulates data needed by channel handlers to deliver notifications.
     */
    record NotificationRequest(
            UUID eventId,
            UUID parcelId,
            String trackingNumber,
            UUID recipientRef,
            String recipientTarget, // email address or phone number
            String recipientName,
            ParcelStatus status
    ) {}
}

package lk.ac.mrt.swiftdrop.notification_service.channel;

import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.service.EmailNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation handling Email notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannelHandler implements NotificationChannelHandler {

    private final EmailNotificationService emailService;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.EMAIL;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public void send(NotificationRequest request) {
        log.info("Dispatching email notification for trackingNumber: {}", request.trackingNumber());
        emailService.sendTemplatedEmail(
                request.recipientTarget(),
                request.trackingNumber(),
                request.status(),
                request.recipientName(),
                null
        );
    }
}

package lk.ac.mrt.swiftdrop.notification_service.channel;

import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.service.SmsProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Strategy implementation handling SMS notifications using a pluggable SMS provider.
 * <p>
 * Configurable via `app.notification.sms.provider`. Defaults to mock if property missing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notification.sms.provider", havingValue = "mock", matchIfMissing = true)
public class MockSmsChannelHandler implements NotificationChannelHandler {

    private final SmsProvider smsProvider;

    @Override
    public boolean supports(NotificationChannel channel) {
        return channel == NotificationChannel.SMS;
    }

    @Override
    public NotificationChannel getChannel() {
        return NotificationChannel.SMS;
    }

    @Override
    public void send(NotificationRequest request) {
        String message = smsProvider.buildMessage(request.trackingNumber(), request.status());
        log.info("Dispatching SMS notification for trackingNumber: {}", request.trackingNumber());
        smsProvider.sendSms(request.recipientTarget(), message);
    }
}

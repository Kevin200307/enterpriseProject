package lk.ac.mrt.swiftdrop.notification_service.dto;

import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;

public final class NotificationLogViews {

    private NotificationLogViews() {}

    public record MyNotificationView(
            UUID id,
            NotificationChannel channel,
            String templateKey,
            NotificationStatus status,
            String trackingNumber,
            Instant createdAt,
            Instant sentAt
    ) {
        public static MyNotificationView from(NotificationLog log) {
            return new MyNotificationView(log.getId(), log.getChannel(), log.getTemplate(), log.getStatus(),
                    log.getTrackingNumber(), log.getCreatedAt(), log.getSentAt());
        }
    }

    public record AdminNotificationView(
            UUID id,
            UUID eventId,
            NotificationChannel channel,
            UUID recipientRef,
            String templateKey,
            NotificationStatus status,
            int attempts,
            String lastError,
            Instant createdAt,
            Instant sentAt
    ) {
        public static AdminNotificationView from(NotificationLog log) {
            return new AdminNotificationView(log.getId(), log.getEventId(), log.getChannel(),
                    log.getRecipientRef(), log.getTemplate(), log.getStatus(), log.getAttempts(),
                    log.getErrorMessage(), log.getCreatedAt(), log.getSentAt());
        }
    }
}

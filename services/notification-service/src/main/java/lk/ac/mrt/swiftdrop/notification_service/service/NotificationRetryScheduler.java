package lk.ac.mrt.swiftdrop.notification_service.service;

import java.time.Instant;
import java.util.List;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationRetryScheduler {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private final NotificationLogRepository logRepository;
    private final EmailNotificationService emailService;
    private final MockSmsNotificationService smsService;
    private final IdentityClient identityClient;

    /**
     * Periodically retries FAILED notifications with exponential backoff (up to 5 attempts).
     * Runs every 15 seconds.
     */
    @Scheduled(fixedDelayString = "${app.notification.retry.interval-ms:15000}")
    public void retryFailedNotifications() {
        List<NotificationLog> failedNotifications = 
                logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, MAX_RETRY_ATTEMPTS);

        if (failedNotifications.isEmpty()) {
            return;
        }

        log.info("Found {} failed notifications eligible for retry.", failedNotifications.size());

        for (NotificationLog notification : failedNotifications) {
            // Exponential backoff: 2^attempts seconds delay
            long backoffSeconds = (long) Math.pow(2, notification.getAttempts());
            Instant eligibleTime = notification.getCreatedAt().plusSeconds(backoffSeconds);

            if (Instant.now().isBefore(eligibleTime)) {
                // Not yet ready for next exponential backoff attempt
                continue;
            }

            notification.setAttempts(notification.getAttempts() + 1);
            log.info("Retrying notification id={}, attempt {}/{}", 
                    notification.getId(), notification.getAttempts(), MAX_RETRY_ATTEMPTS);

            try {
                IdentityClient.UserLookupDto user = identityClient.lookupUser(notification.getRecipientRef());
                if (notification.getChannel() == NotificationChannel.EMAIL) {
                    emailService.sendEmail(user.email(), notification.getSubject(), notification.getContent());
                } else if (notification.getChannel() == NotificationChannel.SMS) {
                    smsService.sendSms(user.phone(), notification.getContent());
                }

                // If succeeded:
                notification.setStatus(NotificationStatus.SENT);
                notification.setSentAt(Instant.now());
                notification.setErrorMessage(null);
                log.info("Retry SUCCESS for notification id={}", notification.getId());
            } catch (Exception e) {
                log.warn("Retry FAILED for notification id={}, attempt {}: {}", 
                        notification.getId(), notification.getAttempts(), e.getMessage());
                notification.setErrorMessage("Attempt " + notification.getAttempts() + " failed: " + e.getMessage());
            }

            logRepository.save(notification);
        }
    }
}
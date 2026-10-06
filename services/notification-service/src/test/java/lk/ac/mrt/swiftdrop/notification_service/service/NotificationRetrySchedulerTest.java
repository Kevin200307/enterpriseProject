package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRetrySchedulerTest {

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private MockSmsNotificationService smsService;

    @InjectMocks
    private NotificationRetryScheduler retryScheduler;

    @Test
    @DisplayName("Should retry failed notification and mark as SENT upon success")
    void shouldRetryFailedNotificationAndMarkSent() {
        NotificationLog failedLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-12345678")
                .channel(NotificationChannel.EMAIL)
                .recipientRef(UUID.randomUUID())
                .recipient("test@example.com")
                .subject("Test Subject")
                .content("Test Content")
                .status(NotificationStatus.FAILED)
                .attempts(1)
                .createdAt(Instant.now().minusSeconds(10)) // well past 2^1 = 2s backoff
                .build();

        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5))
                .thenReturn(List.of(failedLog));

        retryScheduler.retryFailedNotifications();

        verify(emailService, times(1)).sendEmail("test@example.com", "Test Subject", "Test Content");
        assertEquals(NotificationStatus.SENT, failedLog.getStatus());
        assertEquals(2, failedLog.getAttempts());
        verify(logRepository, times(1)).save(failedLog);
    }

    @Test
    @DisplayName("Should skip retry if backoff window has not elapsed")
    void shouldSkipRetryIfBackoffNotElapsed() {
        NotificationLog recentFailedLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-12345678")
                .channel(NotificationChannel.EMAIL)
                .recipientRef(UUID.randomUUID())
                .recipient("test@example.com")
                .status(NotificationStatus.FAILED)
                .attempts(3) // 2^3 = 8s
                .createdAt(Instant.now().minusSeconds(2)) // only 2s ago
                .build();

        when(logRepository.findByStatusAndAttemptsLessThan(NotificationStatus.FAILED, 5))
                .thenReturn(List.of(recentFailedLog));

        retryScheduler.retryFailedNotifications();

        verify(emailService, never()).sendEmail(any(), any(), any());
        verify(logRepository, never()).save(recentFailedLog);
    }
}

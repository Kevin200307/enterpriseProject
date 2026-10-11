package lk.ac.mrt.swiftdrop.notification_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private NotificationLogRepository logRepository;

    @InjectMocks
    private NotificationController notificationController;

    @Test
    @DisplayName("Should return paginated notifications with filters")
    void shouldReturnPaginatedNotificationsWithFilters() {
        NotificationLog mockLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.EMAIL)
                .recipient("te****@example.com")
                .status(NotificationStatus.SENT)
                .template("DELIVERED")
                .createdAt(Instant.now())
                .build();

        Pageable pageable = PageRequest.of(0, 10);
        Page<NotificationLog> page = new PageImpl<>(List.of(mockLog), pageable, 1);
        Instant from = Instant.now().minusSeconds(3600);
        Instant to = Instant.now();

        when(notificationService.getNotificationLogs(NotificationStatus.SENT, NotificationChannel.EMAIL, from, to, pageable))
                .thenReturn(page);

        ResponseEntity<Page<NotificationLog>> response =
                notificationController.getNotifications(NotificationStatus.SENT, NotificationChannel.EMAIL, from, to, pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("SD-11223344", response.getBody().getContent().get(0).getTrackingNumber());
        verify(notificationService, times(1)).getNotificationLogs(NotificationStatus.SENT, NotificationChannel.EMAIL, from, to, pageable);
    }

    @Test
    @DisplayName("Should return empty page when no notifications match filters")
    void shouldReturnEmptyPageWhenNoMatch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<NotificationLog> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(notificationService.getNotificationLogs(null, null, null, null, pageable))
                .thenReturn(emptyPage);

        ResponseEntity<Page<NotificationLog>> response =
                notificationController.getNotifications(null, null, null, null, pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getTotalElements());
        verify(notificationService, times(1)).getNotificationLogs(null, null, null, null, pageable);
    }

    @Test
    @DisplayName("Should return notification logs for a tracking number")
    void shouldReturnNotificationsByTrackingNumber() {
        String trackingNumber = "SD-11223344";
        NotificationLog mockLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .channel(NotificationChannel.EMAIL)
                .recipient("user@example.com")
                .status(NotificationStatus.SENT)
                .template("CREATED")
                .createdAt(Instant.now())
                .build();

        when(logRepository.findByTrackingNumberOrderByCreatedAtDesc(trackingNumber))
                .thenReturn(List.of(mockLog));

        ResponseEntity<List<NotificationLog>> response =
                notificationController.getNotificationsByTrackingNumber(trackingNumber);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(trackingNumber, response.getBody().get(0).getTrackingNumber());
        verify(logRepository, times(1)).findByTrackingNumberOrderByCreatedAtDesc(trackingNumber);
    }

    @Test
    @DisplayName("Should resend notification by ID successfully")
    void shouldResendNotificationById() {
        UUID id = UUID.randomUUID();
        NotificationLog mockResent = NotificationLog.builder()
                .id(id)
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.EMAIL)
                .recipient("te****@example.com")
                .status(NotificationStatus.SENT)
                .template("DELIVERED")
                .attempts(2)
                .createdAt(Instant.now())
                .build();

        when(notificationService.resendNotification(id)).thenReturn(mockResent);

        ResponseEntity<NotificationLog> response = notificationController.resendNotification(id);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().getId());
        assertEquals(NotificationStatus.SENT, response.getBody().getStatus());
        assertEquals(2, response.getBody().getAttempts());
        verify(notificationService, times(1)).resendNotification(id);
    }

    @Test
    @DisplayName("Should return healthy status on /health endpoint")
    void shouldReturnHealthStatus() {
        ResponseEntity<Map<String, String>> response = notificationController.healthCheck();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("notification-service", response.getBody().get("service"));
    }
}

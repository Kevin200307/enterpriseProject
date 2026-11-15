package lk.ac.mrt.swiftdrop.notification_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationLogViews.AdminNotificationView;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationLogViews.MyNotificationView;
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

        when(notificationService.getNotificationLogs(NotificationStatus.SENT, NotificationChannel.EMAIL,
                from, to, pageable))
                .thenReturn(page);

        ResponseEntity<Page<AdminNotificationView>> response =
                notificationController.getNotifications(NotificationStatus.SENT, NotificationChannel.EMAIL,
                        from.toString(), to.toString(), pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getTotalElements());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("DELIVERED", response.getBody().getContent().get(0).templateKey());
        verify(notificationService, times(1)).getNotificationLogs(NotificationStatus.SENT,
                NotificationChannel.EMAIL, from, to, pageable);
    }

    @Test
    @DisplayName("Should return empty page when no notifications match filters")
    void shouldReturnEmptyPageWhenNoMatch() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<NotificationLog> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(notificationService.getNotificationLogs(null, null, null, null, pageable))
                .thenReturn(emptyPage);

        ResponseEntity<Page<AdminNotificationView>> response =
                notificationController.getNotifications(null, null, null, null, pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().getTotalElements());
        verify(notificationService, times(1)).getNotificationLogs(null, null, null, null, pageable);
    }

    @Test
    @DisplayName("Date-only admin filters include the full UTC end date")
    void dateOnlyFiltersCoverFullUtcDays() {
        Pageable pageable = PageRequest.of(0, 20);
        when(notificationService.getNotificationLogs(
                null,
                null,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T23:59:59.999999999Z"),
                pageable))
                .thenReturn(Page.empty(pageable));

        notificationController.getNotifications(null, null, "2026-10-01", "2026-10-02", pageable);

        verify(notificationService).getNotificationLogs(
                null,
                null,
                Instant.parse("2026-10-01T00:00:00Z"),
                Instant.parse("2026-10-02T23:59:59.999999999Z"),
                pageable);
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

        ResponseEntity<List<AdminNotificationView>> response =
                notificationController.getNotificationsByTrackingNumber(trackingNumber);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("CREATED", response.getBody().get(0).templateKey());
        assertNull(response.getBody().get(0).recipientRef());
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

        ResponseEntity<AdminNotificationView> response = notificationController.resendNotification(id);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(id, response.getBody().id());
        assertEquals(NotificationStatus.SENT, response.getBody().status());
        assertEquals(2, response.getBody().attempts());
        verify(notificationService, times(1)).resendNotification(id);
    }

    @Test
    @DisplayName("Should return user notifications using X-User-Id header")
    void shouldReturnUserNotificationsUsingHeader() {
        UUID userId = UUID.randomUUID();
        NotificationLog mockLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .recipientRef(userId)
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .template("CREATED")
                .createdAt(Instant.now())
                .build();

        Pageable pageable = PageRequest.of(0, 20);
        when(notificationService.getUserNotifications(userId, pageable))
                .thenReturn(new PageImpl<>(List.of(mockLog), pageable, 1));

        ResponseEntity<Page<MyNotificationView>> response =
                notificationController.getMyNotifications(userId, pageable);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals("SD-11223344", response.getBody().getContent().get(0).trackingNumber());
        assertEquals("CREATED", response.getBody().getContent().get(0).templateKey());
        verify(notificationService, times(1)).getUserNotifications(userId, pageable);
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

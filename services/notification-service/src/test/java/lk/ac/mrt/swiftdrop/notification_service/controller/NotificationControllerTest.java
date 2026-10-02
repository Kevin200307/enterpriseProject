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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationLogRepository logRepository;

    @InjectMocks
    private NotificationController notificationController;

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
    @DisplayName("Should return healthy status on /health endpoint")
    void shouldReturnHealthStatus() {
        ResponseEntity<Map<String, String>> response = notificationController.healthCheck();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("notification-service", response.getBody().get("service"));
    }
}

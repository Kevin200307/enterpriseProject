package lk.ac.mrt.swiftdrop.notification_service.controller;

import java.util.List;
import java.util.Map;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationLogRepository logRepository;

    /**
     * Retrieve all notification logs for a specific parcel.
     * GET /api/v1/notifications/{trackingNumber}
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<List<NotificationLog>> getNotificationsByTrackingNumber(
            @PathVariable String trackingNumber) {
        log.info("Fetching notification history for trackingNumber={}", trackingNumber);
        List<NotificationLog> logs = logRepository.findByTrackingNumberOrderByCreatedAtDesc(trackingNumber);
        return ResponseEntity.ok(logs);
    }

    /**
     * Simple health/ping endpoint for sanity checks.
     * GET /api/v1/notifications/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "service", "notification-service",
                "status", "UP"
        ));
    }
}
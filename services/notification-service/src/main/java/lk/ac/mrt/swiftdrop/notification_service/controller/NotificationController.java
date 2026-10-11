package lk.ac.mrt.swiftdrop.notification_service.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationLogRepository logRepository;

    /**
     * Admin Delivery Log API:
     * Paginated notification delivery logs filterable by status, channel, and date range.
     * GET /api/v1/notifications
     */
    @GetMapping
    public ResponseEntity<Page<NotificationLog>> getNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Admin query delivery logs: status={}, channel={}, from={}, to={}, pageable={}",
                status, channel, from, to, pageable);
        Page<NotificationLog> page = notificationService.getNotificationLogs(status, channel, from, to, pageable);
        return ResponseEntity.ok(page);
    }

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
     * Resend an existing notification immediately by ID.
     * POST /api/v1/notifications/{id}/resend
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<NotificationLog> resendNotification(@PathVariable UUID id) {
        log.info("Request to resend notification with id={}", id);
        NotificationLog resent = notificationService.resendNotification(id);
        return ResponseEntity.ok(resent);
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
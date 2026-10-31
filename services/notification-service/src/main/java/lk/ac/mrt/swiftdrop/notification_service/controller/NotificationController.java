package lk.ac.mrt.swiftdrop.notification_service.controller;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationLogViews.AdminNotificationView;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationLogViews.MyNotificationView;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
    public ResponseEntity<Page<AdminNotificationView>> getNotifications(
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Instant fromInstant = parseDateFilter(from, false);
        Instant toInstant = parseDateFilter(to, true);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        log.info("Admin query delivery logs: status={}, channel={}, from={}, to={}, pageable={}",
                status, channel, fromInstant, toInstant, pageable);
        Page<AdminNotificationView> page = notificationService
                .getNotificationLogs(status, channel, fromInstant, toInstant, pageable)
                .map(AdminNotificationView::from);
        return ResponseEntity.ok(page);
    }

    private static Instant parseDateFilter(String value, boolean endOfDay) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            return (endOfDay ? date.atTime(LocalTime.MAX) : date.atStartOfDay())
                    .toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignoredInstant) {
                try {
                    return OffsetDateTime.parse(value).toInstant();
                } catch (DateTimeParseException invalid) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Date filters must be ISO dates or timestamps");
                }
            }
        }
    }

    /**
     * User Notification History Endpoint:
     * Retrieve notification history for the authenticated user.
     * Identity is resolved only from the trusted gateway-injected X-User-Id header.
     * GET /api/v1/notifications/me
     */
    @GetMapping("/me")
    public ResponseEntity<Page<MyNotificationView>> getMyNotifications(
            @RequestHeader("X-User-Id") UUID userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Fetching notification history for current user: userId={}", userId);
        Page<MyNotificationView> history = notificationService.getUserNotifications(userId, pageable)
                .map(MyNotificationView::from);
        return ResponseEntity.ok(history);
    }

    /**
     * Retrieve all notification logs for a specific parcel.
     * GET /api/v1/notifications/{trackingNumber}
     */
    @GetMapping("/{trackingNumber}")
    public ResponseEntity<List<AdminNotificationView>> getNotificationsByTrackingNumber(
            @PathVariable String trackingNumber) {
        log.info("Fetching notification history for trackingNumber={}", trackingNumber);
        List<AdminNotificationView> views = logRepository
                .findByTrackingNumberOrderByCreatedAtDesc(trackingNumber)
                .stream()
                .map(AdminNotificationView::from)
                .toList();
        return ResponseEntity.ok(views);
    }

    /**
     * Resend an existing notification immediately by ID.
     * POST /api/v1/notifications/{id}/resend
     */
    @PostMapping("/{id}/resend")
    public ResponseEntity<AdminNotificationView> resendNotification(@PathVariable UUID id) {
        log.info("Request to resend notification with id={}", id);
        NotificationLog resent = notificationService.resendNotification(id);
        return ResponseEntity.ok(AdminNotificationView.from(resent));
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
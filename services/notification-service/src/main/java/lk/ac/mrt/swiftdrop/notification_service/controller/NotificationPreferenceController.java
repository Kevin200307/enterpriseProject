package lk.ac.mrt.swiftdrop.notification_service.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationPreferenceDto;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationPreferenceUpdateRequest;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationPreferenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing GET and PUT /api/v1/notifications/preferences.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<NotificationPreferenceDto> getPreferences(
            @RequestHeader("X-User-Id") UUID userId) {
        log.info("Fetching notification preferences for userId={}", userId);
        NotificationPreference preference = preferenceService.getPreferences(userId);
        return ResponseEntity.ok(NotificationPreferenceDto.fromEntity(preference));
    }

    @PutMapping
    public ResponseEntity<NotificationPreferenceDto> updatePreferences(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody NotificationPreferenceUpdateRequest request) {
        log.info("Updating notification preferences for userId={}", userId);
        NotificationPreference updated = preferenceService.updatePreferences(request.forUser(userId));
        return ResponseEntity.ok(NotificationPreferenceDto.fromEntity(updated));
    }
}

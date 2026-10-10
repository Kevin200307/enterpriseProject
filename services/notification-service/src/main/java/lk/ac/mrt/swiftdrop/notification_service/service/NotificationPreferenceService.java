package lk.ac.mrt.swiftdrop.notification_service.service;

import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.dto.NotificationPreferenceDto;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    @Transactional(readOnly = true)
    public NotificationPreference getPreferences(UUID userId) {
        return repository.findById(userId)
                .orElseGet(() -> NotificationPreference.defaultFor(userId));
    }

    @Transactional
    public NotificationPreference updatePreferences(NotificationPreferenceDto dto) {
        NotificationPreference preference = repository.findById(dto.userId())
                .orElseGet(() -> NotificationPreference.defaultFor(dto.userId()));

        preference.setEmailEnabled(dto.emailEnabled());
        preference.setSmsEnabled(dto.smsEnabled());
        preference.setParsedSmsStatuses(dto.smsStatuses());
        preference.setUpdatedAt(Instant.now());

        log.info("Updated notification preferences for userId: {}", dto.userId());
        return repository.save(preference);
    }
}

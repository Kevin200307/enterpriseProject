package lk.ac.mrt.swiftdrop.notification_service.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.notification_service.channel.ChannelRouter;
import lk.ac.mrt.swiftdrop.notification_service.channel.NotificationChannelHandler;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.logging.PiiMaskingConverter;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final ChannelRouter channelRouter;
    private final EmailNotificationService emailService;
    private final SmsProvider smsProvider;
    private final NotificationLogRepository logRepository;
    private final IdentityClient identityClient;
    private final NotificationPreferenceService preferenceService;

    @Value("${NOTIFICATIONS_ENABLED:${app.notifications.enabled:true}}")
    private boolean notificationsEnabled;

    @Transactional
    public void handleParcelStatusChanged(EventEnvelope<ParcelStatusChangedV2> envelope) {
        if (!notificationsEnabled) {
            log.info("Global kill switch active (NOTIFICATIONS_ENABLED=false). Skipping event: {}", envelope.eventId());
            return;
        }

        UUID eventId = envelope.eventId();
        ParcelStatusChangedV2 payload = envelope.payload();
        String trackingNumber = payload.trackingNumber();
        ParcelStatus toStatus = payload.toStatus();

        UUID recipientRef = payload.customerUserId();

        log.info("Processing notifications for eventId: {}, trackingNumber: {}, recipientRef: {}, changedByUserId: {}, status: {}",
                eventId, trackingNumber, recipientRef, payload.changedByUserId(), toStatus);

        // 1. Resolve user details via Identity Service lookup (or resilient fallback)
        IdentityClient.UserLookupDto user = identityClient.lookupUser(recipientRef);

        // 2. Fetch User Notification Preferences (defaults if none saved)
        NotificationPreference preferences = preferenceService.getPreferences(recipientRef);

        // 3. Process EMAIL Notification (Idempotency: event_id + channel + recipient_ref)
        if (!logRepository.existsByEventIdAndChannelAndRecipientRef(eventId, NotificationChannel.EMAIL, recipientRef)) {
            boolean emailEligible = preferences.isEmailEnabled()
                    && preferences.getParsedEmailStatuses().contains(toStatus);
            if (emailEligible) {
                sendEmail(eventId, payload, recipientRef, user.email(), user.name(), toStatus);
            } else {
                log.info("Email not eligible by user preferences (status: {}, enabled: {}) for recipientRef: {}, recording as SKIPPED",
                        toStatus, preferences.isEmailEnabled(), recipientRef);
                recordSkipped(eventId, payload, recipientRef, PiiMaskingConverter.maskEmail(user.email()),
                        NotificationChannel.EMAIL, toStatus, "Email disabled by preferences for " + toStatus);
            }
        } else {
            log.info("Email already sent for eventId: {}, skipping.", eventId);
        }

        // 4. Process SMS Notification (Check user preferences and status eligibility)
        if (!logRepository.existsByEventIdAndChannelAndRecipientRef(eventId, NotificationChannel.SMS, recipientRef)) {
            boolean smsEligible = preferences.isSmsEnabled() && preferences.getParsedSmsStatuses().contains(toStatus);
            if (smsEligible) {
                sendSms(eventId, payload, recipientRef, user.phone(), user.name(), toStatus);
            } else {
                log.info("SMS not eligible by user preferences (status: {}, enabled: {}) for recipientRef: {}, recording as SKIPPED",
                        toStatus, preferences.isSmsEnabled(), recipientRef);
                recordSkipped(eventId, payload, recipientRef, smsProvider.maskPhoneNumber(user.phone()), NotificationChannel.SMS, toStatus, "SMS disabled by preferences for " + toStatus);
            }
        } else {
            log.info("SMS already sent for eventId: {}, skipping.", eventId);
        }
    }

    private void sendEmail(UUID eventId, ParcelStatusChangedV2 payload, UUID recipientRef, String email, String recipientName, ParcelStatus status) {
        String subject = emailService.buildSubject(payload.trackingNumber(), status);
        String body = emailService.buildBody(payload.trackingNumber(), status);

        NotificationLog notification = NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(NotificationChannel.EMAIL)
                .recipientRef(recipientRef)
                .recipient(PiiMaskingConverter.maskEmail(email))
                .template(status.name())
                .subject(subject)
                .content(body)
                .attempts(1)
                .build();

        try {
            channelRouter.route(NotificationChannel.EMAIL, new NotificationChannelHandler.NotificationRequest(
                    eventId, payload.parcelId(), payload.trackingNumber(), recipientRef, email, recipientName, status));
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send email to {}", email, e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        logRepository.save(notification);
    }

    private void sendSms(UUID eventId, ParcelStatusChangedV2 payload, UUID recipientRef, String phone, String recipientName, ParcelStatus status) {
        String message = smsProvider.buildMessage(payload.trackingNumber(), status);

        NotificationLog notification = NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(NotificationChannel.SMS)
                .recipientRef(recipientRef)
                .recipient(smsProvider.maskPhoneNumber(phone))
                .template(status.name())
                .content(message)
                .attempts(1)
                .build();

        try {
            channelRouter.route(NotificationChannel.SMS, new NotificationChannelHandler.NotificationRequest(
                    eventId, payload.parcelId(), payload.trackingNumber(), recipientRef, phone, recipientName, status));
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}", smsProvider.maskPhoneNumber(phone), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        logRepository.save(notification);
    }

    private void recordSkipped(UUID eventId, ParcelStatusChangedV2 payload, UUID recipientRef, String recipient,
                               NotificationChannel channel, ParcelStatus status, String reason) {
        logRepository.save(NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(channel)
                .recipientRef(recipientRef)
                .recipient(recipient)
                .template(status.name())
                .status(NotificationStatus.SKIPPED)
                .content(reason)
                .attempts(0)
                .build());
    }

    /**
     * Query paginated notification delivery logs with dynamic filters for status, channel, and date range.
     */
    @Transactional(readOnly = true)
    public Page<NotificationLog> getNotificationLogs(
            NotificationStatus status,
            NotificationChannel channel,
            Instant from,
            Instant to,
            Pageable pageable) {
        Specification<NotificationLog> spec = NotificationLogSpecifications.withFilters(status, channel, from, to);
        return logRepository.findAll(spec, pageable);
    }

    /**
     * Immediately resends an existing notification by ID.
     * Resolves live unmasked recipient details via IdentityClient and routes through ChannelRouter.
     */
    @Transactional
    public NotificationLog resendNotification(UUID id) {
        NotificationLog notification = logRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found with id: " + id));

        notification.setAttempts(notification.getAttempts() + 1);

        IdentityClient.UserLookupDto user = identityClient.lookupUser(notification.getRecipientRef());
        String target = notification.getChannel() == NotificationChannel.EMAIL ? user.email() : user.phone();

        ParcelStatus status;
        try {
            status = ParcelStatus.valueOf(notification.getTemplate());
        } catch (Exception e) {
            status = ParcelStatus.CREATED;
        }

        NotificationChannelHandler.NotificationRequest request = new NotificationChannelHandler.NotificationRequest(
                notification.getEventId(),
                notification.getParcelId(),
                notification.getTrackingNumber(),
                notification.getRecipientRef(),
                target,
                user.name(),
                status
        );

        try {
            channelRouter.route(notification.getChannel(), request);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            notification.setErrorMessage(null);
            log.info("Successfully resent notification id={} to channel={}", id, notification.getChannel());
        } catch (Exception e) {
            log.error("Failed to resend notification id={}", id, e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage("Resend failed: " + e.getMessage());
        }

        return logRepository.save(notification);
    }

    /**
     * Retrieves all notifications sent to a specific user (recipientRef), ordered newest first.
     */
    @Transactional(readOnly = true)
    public List<NotificationLog> getUserNotifications(UUID userId) {
        return logRepository.findByRecipientRefOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationLog> getUserNotifications(UUID userId, Pageable pageable) {
        return logRepository.findByRecipientRefOrderByCreatedAtDesc(userId, pageable);
    }
}

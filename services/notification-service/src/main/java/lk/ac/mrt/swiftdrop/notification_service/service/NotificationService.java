package lk.ac.mrt.swiftdrop.notification_service.service;

import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.notification_service.channel.ChannelRouter;
import lk.ac.mrt.swiftdrop.notification_service.channel.NotificationChannelHandler;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public void handleParcelStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        if (!notificationsEnabled) {
            log.info("Global kill switch active (NOTIFICATIONS_ENABLED=false). Skipping event: {}", envelope.eventId());
            return;
        }

        UUID eventId = envelope.eventId();
        ParcelStatusChangedV1 payload = envelope.payload();
        String trackingNumber = payload.trackingNumber();
        ParcelStatus toStatus = payload.toStatus();

        // Resolve user reference (actorId or parcelId as fallback)
        UUID recipientRef = payload.actorId() != null ? payload.actorId() : payload.parcelId();

        log.info("Processing notifications for eventId: {}, trackingNumber: {}, recipientRef: {}, status: {}",
                eventId, trackingNumber, recipientRef, toStatus);

        // 1. Resolve user details via Identity Service lookup (or resilient fallback)
        IdentityClient.UserLookupDto user = identityClient.lookupUser(recipientRef);

        // 2. Fetch User Notification Preferences (defaults if none saved)
        NotificationPreference preferences = preferenceService.getPreferences(recipientRef);

        // 3. Process EMAIL Notification (Idempotency: event_id + channel + recipient_ref)
        if (!logRepository.existsByEventIdAndChannelAndRecipientRef(eventId, NotificationChannel.EMAIL, recipientRef)) {
            if (preferences.isEmailEnabled()) {
                sendEmail(eventId, payload, recipientRef, user.email(), user.name(), toStatus);
            } else {
                log.info("Email disabled by user preferences for recipientRef: {}, recording as SKIPPED", recipientRef);
                recordSkipped(eventId, payload, recipientRef, user.email(), NotificationChannel.EMAIL, toStatus, "Email disabled in preferences");
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

    private void sendEmail(UUID eventId, ParcelStatusChangedV1 payload, UUID recipientRef, String email, String recipientName, ParcelStatus status) {
        String subject = emailService.buildSubject(payload.trackingNumber(), status);
        String body = emailService.buildBody(payload.trackingNumber(), status);

        NotificationLog notification = NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(NotificationChannel.EMAIL)
                .recipientRef(recipientRef)
                .recipient(email)
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

    private void sendSms(UUID eventId, ParcelStatusChangedV1 payload, UUID recipientRef, String phone, String recipientName, ParcelStatus status) {
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

    private void recordSkipped(UUID eventId, ParcelStatusChangedV1 payload, UUID recipientRef, String recipient,
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
}
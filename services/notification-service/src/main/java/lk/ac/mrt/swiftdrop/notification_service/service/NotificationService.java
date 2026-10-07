package lk.ac.mrt.swiftdrop.notification_service.service;

import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
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

    private final EmailNotificationService emailService;
    private final SmsProvider smsProvider;
    private final NotificationLogRepository logRepository;
    private final IdentityClient identityClient;

    @Value("${app.notification.sms.enabled:true}")
    private boolean smsEnabled;

    @Transactional
    public void handleParcelStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
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

        // 2. Process EMAIL Notification (Idempotency: event_id + channel + recipient_ref)
        if (!logRepository.existsByEventIdAndChannelAndRecipientRef(eventId, NotificationChannel.EMAIL, recipientRef)) {
            sendEmail(eventId, payload, recipientRef, user.email(), user.name(), toStatus);
        } else {
            log.info("Email already sent for eventId: {}, skipping.", eventId);
        }

        // 3. Process SMS Notification (Check user/system preferences)
        if (!logRepository.existsByEventIdAndChannelAndRecipientRef(eventId, NotificationChannel.SMS, recipientRef)) {
            if (smsEnabled) {
                sendSms(eventId, payload, recipientRef, user.phone(), toStatus);
            } else {
                // If preferences stop SMS, record as SKIPPED
                log.info("SMS disabled by preferences, recording as SKIPPED for eventId: {}", eventId);
                logRepository.save(NotificationLog.builder()
                        .eventId(eventId)
                        .parcelId(payload.parcelId())
                        .trackingNumber(trackingNumber)
                        .channel(NotificationChannel.SMS)
                        .recipientRef(recipientRef)
                        .recipient(smsProvider.maskPhoneNumber(user.phone()))
                        .template(toStatus.name())
                        .status(NotificationStatus.SKIPPED)
                        .content("SMS skipped due to preferences")
                        .attempts(0)
                        .build());
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
            emailService.sendTemplatedEmail(email, payload.trackingNumber(), status, recipientName, null);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send email to {}", email, e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        logRepository.save(notification);
    }

    private void sendSms(UUID eventId, ParcelStatusChangedV1 payload, UUID recipientRef, String phone, ParcelStatus status) {
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
            smsProvider.sendSms(phone, message);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}", smsProvider.maskPhoneNumber(phone), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage(e.getMessage());
        }

        logRepository.save(notification);
    }
}
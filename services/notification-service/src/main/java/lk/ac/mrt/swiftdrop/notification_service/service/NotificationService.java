package lk.ac.mrt.swiftdrop.notification_service.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final EmailNotificationService emailService;
    private final MockSmsNotificationService smsService;
    private final NotificationLogRepository logRepository;

    @Transactional
    public void handleParcelStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        UUID eventId = envelope.eventId();
        ParcelStatusChangedV1 payload = envelope.payload();
        String trackingNumber = payload.trackingNumber();
        ParcelStatus toStatus = payload.toStatus();

        log.info("Processing notifications for eventId: {}, trackingNumber: {}, status: {}",
                eventId, trackingNumber, toStatus);

        // Recipient details (derived from tracking for demo)
        String customerEmail = "customer-" + trackingNumber.toLowerCase() + "@example.com";
        String customerPhone = "+94771234567";

        // 1. Process EMAIL Notification (with Idempotency Check)
        if (!logRepository.existsByEventIdAndChannel(eventId, NotificationChannel.EMAIL)) {
            sendEmail(eventId, payload, customerEmail, toStatus);
        } else {
            log.info("Email already sent for eventId: {}, skipping.", eventId);
        }

        // 2. Process SMS Notification (with Idempotency Check)
        if (!logRepository.existsByEventIdAndChannel(eventId, NotificationChannel.SMS)) {
            sendSms(eventId, payload, customerPhone, toStatus);
        } else {
            log.info("SMS already sent for eventId: {}, skipping.", eventId);
        }
    }

    private void sendEmail(UUID eventId, ParcelStatusChangedV1 payload, String email, ParcelStatus status) {
        String subject = emailService.buildSubject(payload.trackingNumber(), status);
        String body = emailService.buildBody(payload.trackingNumber(), status);

        NotificationLog.NotificationLogBuilder logBuilder = NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(NotificationChannel.EMAIL)
                .recipient(email)
                .template(status.name())
                .subject(subject)
                .content(body);

        try {
            emailService.sendEmail(email, subject, body);
            logRepository.save(logBuilder.status(NotificationStatus.SENT).build());
        } catch (Exception e) {
            log.error("Failed to send email to {}", email, e);
            logRepository.save(logBuilder
                    .status(NotificationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }

    private void sendSms(UUID eventId, ParcelStatusChangedV1 payload, String phone, ParcelStatus status) {
        String message = smsService.buildMessage(payload.trackingNumber(), status);

        NotificationLog.NotificationLogBuilder logBuilder = NotificationLog.builder()
                .eventId(eventId)
                .parcelId(payload.parcelId())
                .trackingNumber(payload.trackingNumber())
                .channel(NotificationChannel.SMS)
                .recipient(smsService.maskPhoneNumber(phone))
                .template(status.name())
                .content(message);

        try {
            smsService.sendSms(phone, message);
            logRepository.save(logBuilder.status(NotificationStatus.SENT).build());
        } catch (Exception e) {
            log.error("Failed to send SMS to {}", smsService.maskPhoneNumber(phone), e);
            logRepository.save(logBuilder
                    .status(NotificationStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .build());
        }
    }
}
package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
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

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private SmsProvider smsService;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private IdentityClient identityClient;

    @InjectMocks
    private NotificationService notificationService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.springframework.test.util.ReflectionTestUtils.setField(notificationService, "smsEnabled", true);
    }

    @Test
    @DisplayName("Should send email and SMS when event has not been processed yet (Idempotency)")
    void shouldSendEmailAndSmsWhenNotProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.IN_TRANSIT, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(false);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        when(smsService.buildMessage(anyString(), any())).thenReturn("Message");
        when(smsService.maskPhoneNumber(anyString())).thenReturn("+947****567");

        notificationService.handleParcelStatusChanged(envelope);

        verify(emailService, times(1)).sendEmail(anyString(), eq("Subject"), eq("Body"));
        verify(smsService, times(1)).sendSms(anyString(), eq("Message"));
        verify(logRepository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should skip sending if already sent for eventId and recipient (Idempotent)")
    void shouldSkipIfAlreadySent() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.DELIVERED, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(true);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(true);

        notificationService.handleParcelStatusChanged(envelope);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        verify(smsService, never()).sendSms(anyString(), anyString());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should record FAILED status in logRepository when email sending throws exception")
    void shouldRecordFailedStatusOnException() {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.OUT_FOR_DELIVERY, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(true);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        doThrow(new RuntimeException("Mail server down")).when(emailService).sendEmail(anyString(), anyString(), anyString());

        notificationService.handleParcelStatusChanged(envelope);

        verify(logRepository, times(1)).save(any(NotificationLog.class));
    }
}

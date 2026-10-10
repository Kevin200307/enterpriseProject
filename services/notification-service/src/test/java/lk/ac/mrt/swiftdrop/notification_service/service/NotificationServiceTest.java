package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.notification_service.channel.ChannelRouter;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import lk.ac.mrt.swiftdrop.notification_service.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private ChannelRouter channelRouter;

    @Mock
    private EmailNotificationService emailService;

    @Mock
    private SmsProvider smsService;

    @Mock
    private NotificationLogRepository logRepository;

    @Mock
    private IdentityClient identityClient;

    @Mock
    private NotificationPreferenceService preferenceService;

    @InjectMocks
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "notificationsEnabled", true);
    }

    @Test
    @DisplayName("Should route email and SMS according to preferences when not processed")
    void shouldSendEmailAndSmsWhenNotProcessed() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.DELIVERED, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(preferenceService.getPreferences(any())).thenReturn(
                NotificationPreference.defaultFor(parcelId)); // SMS default allows DELIVERED
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(false);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        when(smsService.buildMessage(anyString(), any())).thenReturn("Message");
        when(smsService.maskPhoneNumber(anyString())).thenReturn("+947****567");

        notificationService.handleParcelStatusChanged(envelope);

        verify(channelRouter, times(1)).route(eq(NotificationChannel.EMAIL), any());
        verify(channelRouter, times(1)).route(eq(NotificationChannel.SMS), any());
        verify(logRepository, times(2)).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should skip SMS and record SKIPPED when user preferences disable SMS")
    void shouldSkipSmsWhenDisabledInPreferences() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.DELIVERED, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        NotificationPreference preferenceNoSms = NotificationPreference.builder()
                .userId(parcelId)
                .emailEnabled(true)
                .smsEnabled(false) // SMS turned off
                .smsStatuses("")
                .build();

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(preferenceService.getPreferences(any())).thenReturn(preferenceNoSms);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(false);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        when(smsService.maskPhoneNumber(anyString())).thenReturn("+947****567");

        notificationService.handleParcelStatusChanged(envelope);

        // Email routed, SMS not routed
        verify(channelRouter, times(1)).route(eq(NotificationChannel.EMAIL), any());
        verify(channelRouter, never()).route(eq(NotificationChannel.SMS), any());
        verify(logRepository, times(2)).save(any(NotificationLog.class)); // 1 SENT email, 1 SKIPPED sms
    }

    @Test
    @DisplayName("Should skip all processing when global kill switch NOTIFICATIONS_ENABLED is false")
    void shouldSkipAllWhenKillSwitchIsActive() {
        ReflectionTestUtils.setField(notificationService, "notificationsEnabled", false);

        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.DELIVERED, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        notificationService.handleParcelStatusChanged(envelope);

        verify(identityClient, never()).lookupUser(any());
        verify(channelRouter, never()).route(any(), any());
        verify(logRepository, never()).save(any());
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
        when(preferenceService.getPreferences(any())).thenReturn(NotificationPreference.defaultFor(parcelId));
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(true);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(true);

        notificationService.handleParcelStatusChanged(envelope);

        verify(channelRouter, never()).route(any(), any());
        verify(logRepository, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Should record FAILED status in logRepository when channel routing throws exception")
    void shouldRecordFailedStatusOnException() {
        UUID parcelId = UUID.randomUUID();
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                parcelId, "SD-12345678", ParcelStatus.CREATED, ParcelStatus.OUT_FOR_DELIVERY, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = EventEnvelope.of(
                ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION, "parcel-service", payload);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(parcelId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(preferenceService.getPreferences(any())).thenReturn(NotificationPreference.defaultFor(parcelId));
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(true);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        doThrow(new RuntimeException("Mail server down")).when(channelRouter).route(eq(NotificationChannel.EMAIL), any());

        notificationService.handleParcelStatusChanged(envelope);

        verify(logRepository, times(1)).save(any(NotificationLog.class));
    }
}

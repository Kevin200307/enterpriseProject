package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

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
        UUID customerId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                parcelId, customerId, courierId, ParcelStatus.DELIVERED);

        when(identityClient.lookupUser(any())).thenReturn(
                new IdentityClient.UserLookupDto(customerId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(preferenceService.getPreferences(any())).thenReturn(
                NotificationPreference.defaultFor(customerId)); // SMS default allows DELIVERED
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.EMAIL), any())).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(any(), eq(NotificationChannel.SMS), any())).thenReturn(false);
        when(emailService.buildSubject(anyString(), any())).thenReturn("Subject");
        when(emailService.buildBody(anyString(), any())).thenReturn("Body");
        when(smsService.buildMessage(anyString(), any())).thenReturn("Message");
        when(smsService.maskPhoneNumber(anyString())).thenReturn("+947****567");

        notificationService.handleParcelStatusChanged(envelope);

        verify(identityClient).lookupUser(customerId);
        verify(identityClient, never()).lookupUser(courierId);

        verify(channelRouter, times(1)).route(eq(NotificationChannel.EMAIL), any());
        verify(channelRouter, times(1)).route(eq(NotificationChannel.SMS), any());

        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(captor.capture());

        List<NotificationLog> saved = captor.getAllValues();
        NotificationLog emailLog = saved.stream()
                .filter(l -> l.getChannel() == NotificationChannel.EMAIL)
                .findFirst().orElseThrow();
        assertEquals("te****@swiftdrop.lk", emailLog.getRecipient(), "Email recipient in DB must be masked");
        assertFalse(emailLog.getRecipient().contains("test@swiftdrop.lk"), "Raw email must never be stored in DB");

        NotificationLog smsLog = saved.stream()
                .filter(l -> l.getChannel() == NotificationChannel.SMS)
                .findFirst().orElseThrow();
        assertEquals("+947****567", smsLog.getRecipient(), "SMS recipient in DB must be masked");
    }

    @Test
    @DisplayName("Should skip SMS and record SKIPPED when user preferences disable SMS")
    void shouldSkipSmsWhenDisabledInPreferences() {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                parcelId, parcelId, UUID.randomUUID(), ParcelStatus.DELIVERED);

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
    @DisplayName("Should skip email when the current status is not selected")
    void shouldSkipEmailWhenStatusIsNotSelected() {
        UUID userId = UUID.randomUUID();
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                UUID.randomUUID(), userId, UUID.randomUUID(), ParcelStatus.DELIVERED);
        NotificationPreference preference = NotificationPreference.builder()
                .userId(userId)
                .emailEnabled(true)
                .emailStatuses("CREATED")
                .smsEnabled(false)
                .smsStatuses("")
                .build();

        when(identityClient.lookupUser(userId)).thenReturn(
                new IdentityClient.UserLookupDto(userId, "test@swiftdrop.lk", "+94712345678", "Test User"));
        when(preferenceService.getPreferences(userId)).thenReturn(preference);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(
                any(), eq(NotificationChannel.EMAIL), eq(userId))).thenReturn(false);
        when(logRepository.existsByEventIdAndChannelAndRecipientRef(
                any(), eq(NotificationChannel.SMS), eq(userId))).thenReturn(false);
        when(smsService.maskPhoneNumber(anyString())).thenReturn("+947****567");

        notificationService.handleParcelStatusChanged(envelope);

        verify(channelRouter, never()).route(eq(NotificationChannel.EMAIL), any());
        verify(channelRouter, never()).route(eq(NotificationChannel.SMS), any());
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(log -> log.getChannel() == NotificationChannel.EMAIL
                        && log.getStatus() == NotificationStatus.SKIPPED));
    }

    @Test
    @DisplayName("Should skip all processing when global kill switch NOTIFICATIONS_ENABLED is false")
    void shouldSkipAllWhenKillSwitchIsActive() {
        ReflectionTestUtils.setField(notificationService, "notificationsEnabled", false);

        UUID parcelId = UUID.randomUUID();
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                parcelId, parcelId, UUID.randomUUID(), ParcelStatus.DELIVERED);

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
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                parcelId, parcelId, UUID.randomUUID(), ParcelStatus.DELIVERED);

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
        EventEnvelope<ParcelStatusChangedV2> envelope = statusEvent(
                parcelId, parcelId, UUID.randomUUID(), ParcelStatus.OUT_FOR_DELIVERY);

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

    @Test
    @DisplayName("Should query logRepository using Specification and Pageable")
    void shouldReturnPaginatedLogsUsingSpecification() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<NotificationLog> mockPage = new PageImpl<>(List.of());
        when(logRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);

        Page<NotificationLog> result = notificationService.getNotificationLogs(
                NotificationStatus.SENT, NotificationChannel.EMAIL, null, null, pageable);

        assertEquals(mockPage, result);
        verify(logRepository, times(1)).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    @DisplayName("Should resend notification successfully and update attempts and sentAt")
    void shouldResendNotificationSuccessfully() {
        UUID id = UUID.randomUUID();
        UUID recipientRef = UUID.randomUUID();
        NotificationLog existing = NotificationLog.builder()
                .id(id)
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.EMAIL)
                .recipientRef(recipientRef)
                .recipient("te****@example.com")
                .template("DELIVERED")
                .status(NotificationStatus.FAILED)
                .attempts(1)
                .build();

        when(logRepository.findById(id)).thenReturn(Optional.of(existing));
        when(identityClient.lookupUser(recipientRef)).thenReturn(
                new IdentityClient.UserLookupDto(recipientRef, "test@example.com", "+94771234567", "Test Customer"));
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationLog resent = notificationService.resendNotification(id);

        assertNotNull(resent);
        assertEquals(NotificationStatus.SENT, resent.getStatus());
        assertEquals(2, resent.getAttempts());
        assertNotNull(resent.getSentAt());
        verify(channelRouter, times(1)).route(eq(NotificationChannel.EMAIL), any());
        verify(logRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("Should mark FAILED when channel routing throws exception during resend")
    void shouldHandleResendFailure() {
        UUID id = UUID.randomUUID();
        UUID recipientRef = UUID.randomUUID();
        NotificationLog existing = NotificationLog.builder()
                .id(id)
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.SMS)
                .recipientRef(recipientRef)
                .recipient("+947****567")
                .template("DELIVERED")
                .status(NotificationStatus.FAILED)
                .attempts(2)
                .build();

        when(logRepository.findById(id)).thenReturn(Optional.of(existing));
        when(identityClient.lookupUser(recipientRef)).thenReturn(
                new IdentityClient.UserLookupDto(recipientRef, "test@example.com", "+94771234567", "Test Customer"));
        doThrow(new RuntimeException("SMS gateway down")).when(channelRouter).route(eq(NotificationChannel.SMS), any());
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationLog resent = notificationService.resendNotification(id);

        assertNotNull(resent);
        assertEquals(NotificationStatus.FAILED, resent.getStatus());
        assertEquals(3, resent.getAttempts());
        assertTrue(resent.getErrorMessage().contains("SMS gateway down"));
        verify(logRepository, times(1)).save(existing);
    }

    @Test
    @DisplayName("Should throw 404 NOT_FOUND when resending non-existent notification ID")
    void shouldThrowNotFoundWhenResendingNonExistentId() {
        UUID nonExistentId = UUID.randomUUID();
        when(logRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> notificationService.resendNotification(nonExistentId));
        verify(channelRouter, never()).route(any(), any());
    }

    @Test
    @DisplayName("Should retrieve user notifications ordered by createdAt desc")
    void shouldGetUserNotifications() {
        UUID userId = UUID.randomUUID();
        NotificationLog mockLog = NotificationLog.builder()
                .id(UUID.randomUUID())
                .recipientRef(userId)
                .trackingNumber("SD-11223344")
                .channel(NotificationChannel.EMAIL)
                .status(NotificationStatus.SENT)
                .build();

        when(logRepository.findByRecipientRefOrderByCreatedAtDesc(userId)).thenReturn(List.of(mockLog));

        List<NotificationLog> results = notificationService.getUserNotifications(userId);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(userId, results.get(0).getRecipientRef());
        verify(logRepository, times(1)).findByRecipientRefOrderByCreatedAtDesc(userId);
    }

    private static EventEnvelope<ParcelStatusChangedV2> statusEvent(
            UUID parcelId, UUID customerId, UUID changedByUserId, ParcelStatus toStatus) {
        ParcelStatusChangedV2 payload = new ParcelStatusChangedV2(
                parcelId,
                "SD-12345678",
                customerId,
                null,
                changedByUserId,
                ParcelStatus.CREATED,
                toStatus,
                changedByUserId,
                null,
                null);
        return EventEnvelope.of(
                ParcelStatusChangedV2.EVENT_TYPE,
                ParcelStatusChangedV2.VERSION,
                "parcel-service",
                payload);
    }
}

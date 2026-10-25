package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private NotificationRenderer notificationRenderer;

    @InjectMocks
    private EmailNotificationService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@swiftdrop.lk");
    }

    @Test
    @DisplayName("Should send templated HTML email using JavaMailSender")
    void shouldSendTemplatedEmail() {
        jakarta.mail.Session session = jakarta.mail.Session.getInstance(new java.util.Properties());
        jakarta.mail.internet.MimeMessage mimeMessage = new jakarta.mail.internet.MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        when(notificationRenderer.render(any(), any(), any(), any())).thenReturn(
                new NotificationRenderer.RenderedEmail("SwiftDrop: Parcel SD-11223344 has been booked", "<html>Body</html>", "Text Body"));

        emailService.sendTemplatedEmail("user@example.com", "SD-11223344", ParcelStatus.CREATED, "Customer", null);

        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("Should delegate buildSubject to notificationRenderer")
    void shouldBuildSubjectForEachStatus() {
        when(notificationRenderer.render(any(), any(), any(), any())).thenReturn(
                new NotificationRenderer.RenderedEmail("SwiftDrop: Parcel SD-11223344 has been booked", "<html>Body</html>", "Text Body"));

        String subject = emailService.buildSubject("SD-11223344", ParcelStatus.CREATED);
        assertTrue(subject.contains("booked"));
    }

    @Test
    @DisplayName("Should delegate buildBody to notificationRenderer")
    void shouldBuildBody() {
        when(notificationRenderer.render(any(), any(), any(), any())).thenReturn(
                new NotificationRenderer.RenderedEmail("Subject", "<html>Body</html>", "Hello Customer,\nYour parcel (SD-11223344) has been successfully delivered!\n/receipt"));

        String body = emailService.buildBody("SD-11223344", ParcelStatus.DELIVERED);
        assertTrue(body.contains("SD-11223344"));
        assertTrue(body.contains("delivered"));
        assertTrue(body.contains("/receipt"));
    }
}

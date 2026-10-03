package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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

    @InjectMocks
    private EmailNotificationService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "no-reply@swiftdrop.lk");
    }

    @Test
    @DisplayName("Should send email using JavaMailSender with correct details")
    void shouldSendEmail() {
        String to = "user@example.com";
        String subject = "Test Subject";
        String body = "Test Body";

        emailService.sendEmail(to, subject, body);

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        SimpleMailMessage sentMessage = messageCaptor.getValue();
        assertEquals("no-reply@swiftdrop.lk", sentMessage.getFrom());
        assertEquals(to, sentMessage.getTo()[0]);
        assertEquals(subject, sentMessage.getSubject());
        assertEquals(body, sentMessage.getText());
    }

    @Test
    @DisplayName("Should build appropriate subjects for each parcel status")
    void shouldBuildSubjectForEachStatus() {
        String tracking = "SD-11223344";

        assertTrue(emailService.buildSubject(tracking, ParcelStatus.CREATED).contains("booked"));
        assertTrue(emailService.buildSubject(tracking, ParcelStatus.PICKED_UP).contains("picked up"));
        assertTrue(emailService.buildSubject(tracking, ParcelStatus.IN_TRANSIT).contains("in transit"));
        assertTrue(emailService.buildSubject(tracking, ParcelStatus.OUT_FOR_DELIVERY).contains("out for delivery"));
        assertTrue(emailService.buildSubject(tracking, ParcelStatus.DELIVERED).contains("delivered"));
        assertTrue(emailService.buildSubject(tracking, ParcelStatus.FAILED).contains("failed"));
    }

    @Test
    @DisplayName("Should build email body containing tracking number and status")
    void shouldBuildBody() {
        String body = emailService.buildBody("SD-11223344", ParcelStatus.DELIVERED);

        assertTrue(body.contains("SD-11223344"));
        assertTrue(body.contains("DELIVERED"));
        assertTrue(body.contains("SwiftDrop"));
    }
}

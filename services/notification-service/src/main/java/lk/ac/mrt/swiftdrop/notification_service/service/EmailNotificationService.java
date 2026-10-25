package lk.ac.mrt.swiftdrop.notification_service.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;
    private final NotificationRenderer notificationRenderer;

    @Value("${app.notification.mail.from:no-reply@swiftdrop.lk}")
    private String fromEmail;

    /**
     * Dispatches an HTML + plain-text multipart email rendered via Thymeleaf templates.
     */
    public void sendTemplatedEmail(String to, String trackingNumber, ParcelStatus status, String recipientName, Map<String, Object> extraParams) {
        NotificationRenderer.RenderedEmail rendered = notificationRenderer.render(trackingNumber, status, recipientName, extraParams);
        sendHtmlEmail(to, rendered.subject(), rendered.htmlBody(), rendered.textBody());
    }

    /**
     * Sends multipart HTML email with plain-text fallback.
     */
    public void sendHtmlEmail(String to, String subject, String htmlBody, String textBody) {
        log.info("Sending HTML email to: {} | Subject: {}", to, subject);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody); // first text, second html (multipart/alternative)

            mailSender.send(mimeMessage);
            log.info("HTML email successfully sent to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to compose or send email to {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    /**
     * Fallback / legacy support for plain-text email sending.
     */
    public void sendEmail(String to, String subject, String body) {
        sendHtmlEmail(to, subject, "<pre>" + body + "</pre>", body);
    }

    public String buildSubject(String trackingNumber, ParcelStatus status) {
        return notificationRenderer.render(trackingNumber, status, "Customer", null).subject();
    }

    public String buildBody(String trackingNumber, ParcelStatus status) {
        return notificationRenderer.render(trackingNumber, status, "Customer", null).textBody();
    }
}
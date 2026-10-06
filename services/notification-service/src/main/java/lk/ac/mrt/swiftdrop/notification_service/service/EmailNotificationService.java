package lk.ac.mrt.swiftdrop.notification_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.mail.from:no-reply@swiftdrop.lk}")
    private String fromEmail;

    @Value("${app.web.base-url:http://localhost:8080}")
    private String baseUrl;

    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to: {} | Subject: {}", to, subject);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        log.info("Email sent successfully to {}", to);
    }

    public String buildSubject(String trackingNumber, ParcelStatus status) {
        return switch (status) {
            case CREATED -> "SwiftDrop: Parcel " + trackingNumber + " has been booked";
            case PICKED_UP -> "SwiftDrop: Parcel " + trackingNumber + " has been picked up";
            case IN_TRANSIT -> "SwiftDrop: Parcel " + trackingNumber + " is in transit";
            case OUT_FOR_DELIVERY -> "SwiftDrop: Parcel " + trackingNumber + " is out for delivery today!";
            case DELIVERED -> "SwiftDrop: Parcel " + trackingNumber + " has been delivered";
            case FAILED -> "SwiftDrop: Delivery attempt failed for parcel " + trackingNumber;
        };
    }

    public String buildBody(String trackingNumber, ParcelStatus status) {
        if (status == ParcelStatus.DELIVERED) {
            return """
                   Hello Customer,

                   Your parcel (%s) has been successfully delivered!

                   You can download your delivery receipt here:
                   %s/api/v1/parcels/%s/receipt

                   Thank you for choosing SwiftDrop!
                   """.formatted(trackingNumber, baseUrl, trackingNumber);
        }

        return """
               Hello Customer,

               The status of your parcel (%s) has been updated to: %s.

               Track your delivery in real-time on our portal.

               Thank you for choosing SwiftDrop!
               """.formatted(trackingNumber, status);
    }
}
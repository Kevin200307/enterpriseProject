package lk.ac.mrt.swiftdrop.notification_service.service;

import java.util.Map;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationRenderer {

    private final TemplateEngine templateEngine;

    @Value("${app.web.base-url:http://localhost:5173}")
    private String webBaseUrl;

    public record RenderedEmail(String subject, String htmlBody, String textBody) {}

    public RenderedEmail render(String trackingNumber, ParcelStatus status, String recipientName, Map<String, Object> extraParams) {
        String trackingUrl = webBaseUrl + "/track/" + trackingNumber;
        String receiptUrl = webBaseUrl + "/api/v1/parcels/" + trackingNumber + "/receipt";

        Context context = new Context();
        context.setVariable("trackingNumber", trackingNumber);
        context.setVariable("status", status.name());
        context.setVariable("recipientName", (recipientName != null && !recipientName.isBlank()) ? recipientName : "Customer");
        context.setVariable("trackingUrl", trackingUrl);
        context.setVariable("receiptUrl", receiptUrl);

        if (extraParams != null) {
            extraParams.forEach(context::setVariable);
        }

        String templateName = resolveTemplateName(status);
        String subject = resolveSubject(trackingNumber, status);
        String htmlBody = templateEngine.process("email/" + templateName, context);
        String textBody = buildPlainTextAlternative(trackingNumber, status, recipientName, trackingUrl, receiptUrl);

        return new RenderedEmail(subject, htmlBody, textBody);
    }

    private String resolveTemplateName(ParcelStatus status) {
        return switch (status) {
            case CREATED -> "booking-confirmed";
            case PICKED_UP -> "picked-up";
            case IN_TRANSIT -> "in-transit";
            case OUT_FOR_DELIVERY -> "out-for-delivery";
            case DELIVERED -> "delivered";
            case FAILED -> "delivery-failed";
        };
    }

    private String resolveSubject(String trackingNumber, ParcelStatus status) {
        return switch (status) {
            case CREATED -> "SwiftDrop: Parcel " + trackingNumber + " has been booked";
            case PICKED_UP -> "SwiftDrop: Parcel " + trackingNumber + " has been picked up";
            case IN_TRANSIT -> "SwiftDrop: Parcel " + trackingNumber + " is in transit";
            case OUT_FOR_DELIVERY -> "SwiftDrop: Parcel " + trackingNumber + " is out for delivery today!";
            case DELIVERED -> "SwiftDrop: Parcel " + trackingNumber + " has been delivered";
            case FAILED -> "SwiftDrop: Delivery attempt failed for parcel " + trackingNumber;
        };
    }

    private String buildPlainTextAlternative(String trackingNumber, ParcelStatus status, String recipientName, String trackingUrl, String receiptUrl) {
        String name = (recipientName != null && !recipientName.isBlank()) ? recipientName : "Customer";
        StringBuilder sb = new StringBuilder();
        sb.append("Hello ").append(name).append(",\n\n");

        switch (status) {
            case CREATED -> sb.append("Your parcel (").append(trackingNumber).append(") has been booked successfully.\n");
            case PICKED_UP -> sb.append("Our courier partner has picked up your parcel (").append(trackingNumber).append(").\n");
            case IN_TRANSIT -> sb.append("Your parcel (").append(trackingNumber).append(") is currently in transit.\n");
            case OUT_FOR_DELIVERY -> sb.append("Your parcel (").append(trackingNumber).append(") is out for delivery today!\n");
            case DELIVERED -> {
                sb.append("Your parcel (").append(trackingNumber).append(") has been successfully delivered!\n");
                sb.append("Download delivery receipt: ").append(receiptUrl).append("\n");
            }
            case FAILED -> sb.append("A delivery attempt for parcel (").append(trackingNumber).append(") was unsuccessful. We will reschedule shortly.\n");
        }

        sb.append("\nTrack your parcel online: ").append(trackingUrl).append("\n\n");
        sb.append("Thank you for choosing SwiftDrop!\n");
        sb.append("SwiftDrop Logistics Network\n");
        return sb.toString();
    }
}

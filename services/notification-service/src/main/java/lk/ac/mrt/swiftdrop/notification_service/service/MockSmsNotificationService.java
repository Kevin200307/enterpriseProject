package lk.ac.mrt.swiftdrop.notification_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MockSmsNotificationService {

    public void sendSms(String phoneNumber, String message) {
        String maskedNumber = maskPhoneNumber(phoneNumber);
        // Security requirement: Never log raw unmasked phone numbers in plain logs
        log.info("[MOCK SMS DISPATCHED] To: {} | Message: {}", maskedNumber, message);
    }

    public String buildMessage(String trackingNumber, ParcelStatus status) {
        return "SwiftDrop: Parcel " + trackingNumber + " is now " + status + ". Track at swiftdrop.lk";
    }

    public String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 3);
    }
}
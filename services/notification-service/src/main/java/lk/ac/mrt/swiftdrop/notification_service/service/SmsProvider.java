package lk.ac.mrt.swiftdrop.notification_service.service;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

public interface SmsProvider {

    void sendSms(String phoneNumber, String message);

    String buildMessage(String trackingNumber, ParcelStatus status);

    String maskPhoneNumber(String phone);
}

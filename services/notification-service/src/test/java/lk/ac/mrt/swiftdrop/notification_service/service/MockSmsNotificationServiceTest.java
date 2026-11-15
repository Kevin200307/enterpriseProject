package lk.ac.mrt.swiftdrop.notification_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MockSmsNotificationServiceTest {

    private MockSmsNotificationService smsService;

    @BeforeEach
    void setUp() {
        smsService = new MockSmsNotificationService();
    }

    @Test
    @DisplayName("Should mask phone numbers correctly to preserve PII")
    void shouldMaskPhoneNumber() {
        String phone = "+94771234567";
        String masked = smsService.maskPhoneNumber(phone);

        assertEquals("+947****567", masked);
    }

    @Test
    @DisplayName("Should return fallback mask for short or invalid phone numbers")
    void shouldHandleShortPhoneNumbers() {
        assertEquals("***", smsService.maskPhoneNumber("12345"));
        assertEquals("***", smsService.maskPhoneNumber(null));
    }

    @Test
    @DisplayName("Should build SMS message with tracking number and status")
    void shouldBuildMessage() {
        String message = smsService.buildMessage("SD-99887766", ParcelStatus.OUT_FOR_DELIVERY);

        assertTrue(message.contains("SD-99887766"));
        assertTrue(message.contains("OUT_FOR_DELIVERY"));
    }
}

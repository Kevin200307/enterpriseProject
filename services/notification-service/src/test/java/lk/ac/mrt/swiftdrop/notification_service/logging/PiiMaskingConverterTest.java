package lk.ac.mrt.swiftdrop.notification_service.logging;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PiiMaskingConverterTest {

    private PiiMaskingConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PiiMaskingConverter();
    }

    @Test
    @DisplayName("Should redact email addresses in log lines")
    void shouldMaskEmailInLogLines() {
        String logMessage = "Dispatched notification to customer john.doe@example.com for parcel SD-1234";
        String transformed = converter.transform(null, logMessage);

        assertFalse(transformed.contains("john.doe@example.com"), "Raw email must be absent");
        assertTrue(transformed.contains("j****@example.com") || transformed.contains("****@example.com"));
    }

    @Test
    @DisplayName("Should redact phone numbers in log lines")
    void shouldMaskPhoneNumberInLogLines() {
        String logMessage = "SMS sent to phone +94771234567 successfully";
        String transformed = converter.transform(null, logMessage);

        assertFalse(transformed.contains("+94771234567"), "Raw phone number must be absent");
        assertTrue(transformed.contains("****"));
    }

    @Test
    @DisplayName("Should mask email using helper method")
    void shouldMaskEmailUsingHelper() {
        String masked = PiiMaskingConverter.maskEmail("thiranjaya@gmail.com");
        assertFalse(masked.contains("thiranjaya"));
        assertTrue(masked.startsWith("th****@gmail.com"));
    }
}
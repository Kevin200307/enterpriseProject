package lk.ac.mrt.swiftdrop.merchant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WebhookCryptoTest {
    @Test void matchesTimestampAndRawBodySignatureFixture() {
        assertEquals("b8569b78799ff9e3cbff0fc2d63a33a2b57f3282abd07c37ae5e8e7d79a5f163",
                WebhookCrypto.sign("secret", 1700000000L, "{}"));
    }
    @Test void encryptedSecretCanBeRotatedWithoutStoringPlaintext() {
        WebhookCrypto crypto = new WebhookCrypto("master-key");
        String first = crypto.encrypt("whsec_first");
        String second = crypto.encrypt("whsec_second");
        assertNotEquals(first, second);
        assertFalse(second.contains("whsec_second"));
        assertEquals("whsec_second", crypto.decrypt(second));
    }
    @Test void rejectsUnsafeWebhookUrls() {
        assertThrows(IllegalArgumentException.class, () -> WebhookService.validatePublicHttps("http://example.com/hook"));
        assertThrows(IllegalArgumentException.class, () -> WebhookService.validatePublicHttps("https://127.0.0.1/hook"));
        assertThrows(IllegalArgumentException.class, () -> WebhookService.validatePublicHttps("https://10.0.0.1/hook"));
    }
}

package lk.ac.mrt.swiftdrop.notification_service.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationLogTest {

    @Test
    @DisplayName("Should generate UUID and created_at timestamp on prePersist if null")
    void shouldGenerateIdAndCreatedAtOnPrePersist() {
        NotificationLog log = NotificationLog.builder()
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-12345678")
                .channel(NotificationChannel.EMAIL)
                .recipient("test@example.com")
                .status(NotificationStatus.SENT)
                .template("CREATED")
                .subject("Parcel Created")
                .content("Your parcel has been created.")
                .build();

        log.prePersist();

        assertNotNull(log.getId(), "ID should be generated");
        assertNotNull(log.getCreatedAt(), "CreatedAt should be generated");
        assertEquals("SD-12345678", log.getTrackingNumber());
        assertEquals(NotificationChannel.EMAIL, log.getChannel());
        assertEquals(NotificationStatus.SENT, log.getStatus());
    }

    @Test
    @DisplayName("Should preserve manually assigned ID and createdAt")
    void shouldPreserveManuallyAssignedIdAndCreatedAt() {
        UUID customId = UUID.randomUUID();
        NotificationLog log = NotificationLog.builder()
                .id(customId)
                .eventId(UUID.randomUUID())
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-87654321")
                .channel(NotificationChannel.SMS)
                .recipient("+94771234567")
                .status(NotificationStatus.FAILED)
                .errorMessage("Network timeout")
                .build();

        log.prePersist();

        assertEquals(customId, log.getId(), "Custom ID should not be overridden");
        assertEquals(NotificationChannel.SMS, log.getChannel());
        assertEquals(NotificationStatus.FAILED, log.getStatus());
        assertEquals("Network timeout", log.getErrorMessage());
    }
}

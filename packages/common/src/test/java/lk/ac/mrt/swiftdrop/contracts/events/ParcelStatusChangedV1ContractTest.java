package lk.ac.mrt.swiftdrop.contracts.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class ParcelStatusChangedV1ContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<EventEnvelope<ParcelStatusChangedV1>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    @Test
    void goldenJsonDeserializes() {
        EventEnvelope<ParcelStatusChangedV1> envelope = MAPPER.readValue(golden(), ENVELOPE_TYPE);

        assertEquals(ParcelStatusChangedV1.EVENT_TYPE, envelope.eventType());
        assertEquals(ParcelStatusChangedV1.VERSION, envelope.version());
        assertEquals(Instant.parse("2026-09-29T10:15:30Z"), envelope.occurredAt());
        assertEquals("SD-4F7K2Q9M", envelope.payload().trackingNumber());
        assertEquals(ParcelStatus.PICKED_UP, envelope.payload().fromStatus());
        assertEquals(ParcelStatus.IN_TRANSIT, envelope.payload().toStatus());
        assertNull(envelope.payload().merchantId());
    }

    @Test
    void serializationMatchesGoldenJson() {
        EventEnvelope<ParcelStatusChangedV1> envelope = new EventEnvelope<>(
                UUID.fromString("3f1c2b9e-8a4d-4c6e-9b1a-2d7e5f0c1a23"),
                ParcelStatusChangedV1.EVENT_TYPE,
                ParcelStatusChangedV1.VERSION,
                Instant.parse("2026-09-29T10:15:30Z"),
                "parcel-service",
                new ParcelStatusChangedV1(
                        UUID.fromString("b6a4e1d2-5c3f-4a8b-9e7d-1f2a3b4c5d6e"),
                        "SD-4F7K2Q9M",
                        ParcelStatus.PICKED_UP,
                        ParcelStatus.IN_TRANSIT,
                        UUID.fromString("c9d8e7f6-a5b4-4c3d-8e2f-1a0b9c8d7e6f"),
                        null));

        assertEquals(MAPPER.readTree(golden()), MAPPER.readTree(MAPPER.writeValueAsString(envelope)));
    }

    @Test
    void missingRequiredPayloadFieldIsRejected() {
        String withoutToStatus = golden().replace("\"toStatus\": \"IN_TRANSIT\",", "");

        assertThrows(JacksonException.class, () -> MAPPER.readValue(withoutToStatus, ENVELOPE_TYPE));
    }

    @Test
    void toEnvelopeFillsMetadata() {
        ParcelStatusChangedV1 event = new ParcelStatusChangedV1(
                UUID.randomUUID(), "SD-AAAA0000", null, ParcelStatus.CREATED, null, null);

        EventEnvelope<ParcelStatusChangedV1> envelope = event.toEnvelope("parcel-service");

        assertNotNull(envelope.eventId());
        assertNotNull(envelope.occurredAt());
        assertEquals(ParcelStatusChangedV1.EVENT_TYPE, envelope.eventType());
        assertEquals(ParcelStatusChangedV1.VERSION, envelope.version());
        assertEquals("parcel-service", envelope.producer());
    }

    @Test
    void messagingNamesFollowConvention() {
        assertEquals("parcel.status.changed.v1", RoutingKeys.PARCEL_STATUS_CHANGED_V1);
        assertEquals("notification.parcel.status.changed.v1", Queues.NOTIFICATION_PARCEL_STATUS_CHANGED);
        assertEquals("notification.parcel.status.changed.v1.dlq",
                Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED));
    }

    private static String golden() {
        try (InputStream in = ParcelStatusChangedV1ContractTest.class
                .getResourceAsStream("/golden/parcel.status.changed.v1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

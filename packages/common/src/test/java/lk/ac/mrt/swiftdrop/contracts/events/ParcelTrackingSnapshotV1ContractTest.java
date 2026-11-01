package lk.ac.mrt.swiftdrop.contracts.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1.SnapshotTimelineEntryV1;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ParcelTrackingSnapshotV1ContractTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<EventEnvelope<ParcelTrackingSnapshotV1>> ENVELOPE_TYPE = new TypeReference<>() {
    };

    @Test
    void goldenJsonDeserializes() {
        EventEnvelope<ParcelTrackingSnapshotV1> envelope = MAPPER.readValue(golden(), ENVELOPE_TYPE);

        assertEquals(ParcelTrackingSnapshotV1.EVENT_TYPE, envelope.eventType());
        assertEquals(ParcelTrackingSnapshotV1.VERSION, envelope.version());
        assertEquals(Instant.parse("2026-09-29T10:15:30Z"), envelope.occurredAt());

        ParcelTrackingSnapshotV1 payload = envelope.payload();
        assertEquals("SD-4F7K2Q9M", payload.trackingNumber());
        assertEquals(ParcelStatus.IN_TRANSIT, payload.status());
        assertEquals("Colombo", payload.originCity());
        assertEquals("Kandy", payload.destinationCity());
        assertEquals("Kasun", payload.courierFirstName());
        assertEquals("2026-10-10T09:00:00Z", payload.eta());
        assertEquals(Instant.parse("2026-09-29T10:15:30Z"), payload.updatedAt());

        assertEquals(2, payload.timeline().size());
        assertEquals(ParcelStatus.CREATED, payload.timeline().get(0).status());
        assertEquals("Parcel created", payload.timeline().get(0).note());
        assertEquals(ParcelStatus.IN_TRANSIT, payload.timeline().get(1).status());
        assertEquals("Departed hub", payload.timeline().get(1).note());
    }

    @Test
    void serializationMatchesGoldenJson() {
        EventEnvelope<ParcelTrackingSnapshotV1> envelope = new EventEnvelope<>(
                UUID.fromString("4a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
                ParcelTrackingSnapshotV1.EVENT_TYPE,
                ParcelTrackingSnapshotV1.VERSION,
                Instant.parse("2026-09-29T10:15:30Z"),
                "parcel-service",
                new ParcelTrackingSnapshotV1(
                        UUID.fromString("b6a4e1d2-5c3f-4a8b-9e7d-1f2a3b4c5d6e"),
                        "SD-4F7K2Q9M",
                        ParcelStatus.IN_TRANSIT,
                        "Colombo",
                        "Kandy",
                        "Kasun",
                        "2026-10-10T09:00:00Z",
                        Instant.parse("2026-09-29T10:15:30Z"),
                        List.of(
                                new SnapshotTimelineEntryV1(ParcelStatus.CREATED, Instant.parse("2026-09-29T08:00:00Z"), "Parcel created"),
                                new SnapshotTimelineEntryV1(ParcelStatus.IN_TRANSIT, Instant.parse("2026-09-29T10:15:30Z"), "Departed hub"))));

        assertEquals(MAPPER.readTree(golden()), MAPPER.readTree(MAPPER.writeValueAsString(envelope)));
    }

    @Test
    void goldenJsonContainsNoPiiOrForbiddenFields() {
        JsonNode tree = MAPPER.readTree(golden());
        JsonNode payload = tree.path("payload");

        // Forbidden PII fields
        assertFalse(payload.has("phone"), "Payload must not contain phone");
        assertFalse(payload.has("line1"), "Payload must not contain street line");
        assertFalse(payload.has("recipientName"), "Payload must not contain recipient full name");
        assertFalse(payload.has("customerName"), "Payload must not contain customer full name");
        assertFalse(payload.has("contactName"), "Payload must not contain contact name");
        assertFalse(payload.has("actorId"), "Payload must not contain actorId");
        assertFalse(payload.has("merchantId"), "Payload must not contain merchantId");
    }

    @Test
    void trackingNumberValidationRules() {
        assertTrue(TrackingNumber.isValid("SD-4F7K2Q9M"));
        assertTrue(TrackingNumber.isValid("SD-ABCDEF23"));
        assertTrue(TrackingNumber.isValid("SD-ZZZZ9999"));
        assertTrue(TrackingNumber.isValid("SD-00000001"));
        assertTrue(TrackingNumber.isValid("SD-4F7K2Q90"));

        // Invalid: missing SD- prefix, wrong length or non-base-36 characters
        assertFalse(TrackingNumber.isValid("4F7K2Q9M"));
        assertFalse(TrackingNumber.isValid("SD-4F7K2Q9"));
        assertFalse(TrackingNumber.isValid("SD-4F7K2Q9MM"));
        assertFalse(TrackingNumber.isValid("SD-4F7K2Q9_"));
        assertFalse(TrackingNumber.isValid(null));

        assertThrows(IllegalArgumentException.class, () -> TrackingNumber.validate("invalid-tn"));
    }

    @Test
    void toEnvelopeFillsMetadata() {
        ParcelTrackingSnapshotV1 snapshot = new ParcelTrackingSnapshotV1(
                UUID.randomUUID(),
                "SD-4F7K2Q9M",
                ParcelStatus.CREATED,
                "Colombo",
                "Galle",
                null,
                null,
                Instant.now(),
                List.of());

        EventEnvelope<ParcelTrackingSnapshotV1> envelope = snapshot.toEnvelope("parcel-service");

        assertNotNull(envelope.eventId());
        assertNotNull(envelope.occurredAt());
        assertEquals(ParcelTrackingSnapshotV1.EVENT_TYPE, envelope.eventType());
        assertEquals(ParcelTrackingSnapshotV1.VERSION, envelope.version());
        assertEquals("parcel-service", envelope.producer());
    }

    @Test
    void messagingNamesFollowConvention() {
        assertEquals("parcel.tracking.snapshot.v1", RoutingKeys.PARCEL_TRACKING_SNAPSHOT_V1);
        assertEquals("tracking.parcel.tracking.snapshot.v1", Queues.TRACKING_PARCEL_SNAPSHOT);
        assertEquals("tracking.parcel.tracking.snapshot.v1.dlq", Queues.deadLetterOf(Queues.TRACKING_PARCEL_SNAPSHOT));
    }

    private static String golden() {
        try (InputStream in = ParcelTrackingSnapshotV1ContractTest.class
                .getResourceAsStream("/golden/parcel.tracking.snapshot.v1.json")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

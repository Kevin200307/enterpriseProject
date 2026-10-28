package lk.ac.mrt.swiftdrop.contracts.events;

import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParcelStatusChangedV2ContractTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final TypeReference<EventEnvelope<ParcelStatusChangedV2>> TYPE = new TypeReference<>() {};

    @Test
    void goldenJsonRoundTrips() {
        EventEnvelope<ParcelStatusChangedV2> event = MAPPER.readValue(golden(), TYPE);
        assertEquals(2, event.version());
        assertEquals(ParcelStatus.IN_TRANSIT, event.payload().toStatus());
        assertEquals(MAPPER.readTree(golden()), MAPPER.readTree(MAPPER.writeValueAsString(event)));
    }

    @Test
    void requiredCustomerIsRejectedWhenMissing() {
        String json = golden().replace("\"customerUserId\": \"11111111-1111-1111-1111-111111111111\",", "");
        assertThrows(JacksonException.class, () -> MAPPER.readValue(json, TYPE));
    }

    @Test
    void v1StillDeserializesAndV2ContainsNoContactData() {
        String v1 = resource("/golden/parcel.status.changed.v1.json");
        EventEnvelope<ParcelStatusChangedV1> old = MAPPER.readValue(v1, new TypeReference<>() {});
        assertEquals(1, old.version());
        String v2 = golden().toLowerCase();
        assertFalse(v2.contains("email"));
        assertFalse(v2.contains("phone"));
        assertFalse(v2.contains("address"));
    }

    @Test
    void messagingNamesAreVersioned() {
        assertEquals("parcel.status.changed.v2", RoutingKeys.PARCEL_STATUS_CHANGED_V2);
        assertEquals("notification.parcel.status.changed.v2", Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2);
    }

    private static String golden() { return resource("/golden/parcel.status.changed.v2.json"); }

    private static String resource(String path) {
        try (InputStream in = ParcelStatusChangedV2ContractTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

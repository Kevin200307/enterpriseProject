package lk.ac.mrt.swiftdrop.contracts.events;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import static org.junit.jupiter.api.Assertions.*;

class ParcelDispatchChangedV1ContractTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Test void goldenRoundTripAndRouting() throws Exception {
        try (var in = getClass().getResourceAsStream("/golden/parcel.dispatch.changed.v1.json")) {
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            var envelope = mapper.readValue(json, new TypeReference<EventEnvelope<ParcelDispatchChangedV1>>() {});
            assertEquals(mapper.readTree(json), mapper.readTree(mapper.writeValueAsString(envelope)));
            assertEquals("COLOMBO", envelope.payload().pickup().zoneCode());
            assertEquals("dispatch.parcel.status.changed.v1", Queues.DISPATCH_PARCEL_STATUS_CHANGED);
            assertEquals("dispatch.parcel.dispatch.changed.v1", Queues.DISPATCH_PARCEL_DISPATCH_CHANGED);
            assertEquals("parcel.dispatch.changed.v1", RoutingKeys.PARCEL_DISPATCH_CHANGED_V1);
            assertThrows(RuntimeException.class, () -> mapper.readValue(json.replace("\"SMALL\"", "null"),
                    new TypeReference<EventEnvelope<ParcelDispatchChangedV1>>() {}));
        }
    }
}

package lk.ac.mrt.swiftdrop.parcel_service.messaging;

import java.time.Instant;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelDispatchChangedV1;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import lk.ac.mrt.swiftdrop.parcel_service.domain.*;
import lk.ac.mrt.swiftdrop.parcel_service.repository.OutboxRepository;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DispatchEventWriter {
    private final OutboxRepository outbox;
    private final JsonMapper mapper;
    public DispatchEventWriter(OutboxRepository outbox, JsonMapper mapper) { this.outbox=outbox; this.mapper=mapper; }
    /** Call after flushing the parcel so the snapshot contains its persisted optimistic version. */
    public void write(Parcel parcel, String note, Instant now) {
        var payload = new ParcelDispatchChangedV1(parcel.getId(),parcel.getTrackingNumber(),parcel.getVersion(),
                parcel.getStatus(),parcel.getAssigneeId(),address(parcel.getPickup()),address(parcel.getDropoff()),
                parcel.getSize(),parcel.getWeightKg(),parcel.getCreatedAt(),note);
        var envelope = new lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope<>(java.util.UUID.randomUUID(),
                ParcelDispatchChangedV1.EVENT_TYPE,1,now,"parcel-service",payload);
        outbox.save(new OutboxEvent(envelope.eventId(),mapper.writeValueAsString(envelope),now,RoutingKeys.PARCEL_DISPATCH_CHANGED_V1));
    }
    private ParcelDispatchChangedV1.Address address(Address address) {
        return new ParcelDispatchChangedV1.Address(address.getContactName(),address.getPhone(),address.getLine1(),null,
                address.getCity(),address.getPostalCode(),address.getZone());
    }
}

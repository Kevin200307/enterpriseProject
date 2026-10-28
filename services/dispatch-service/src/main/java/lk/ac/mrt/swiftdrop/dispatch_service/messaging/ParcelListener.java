package lk.ac.mrt.swiftdrop.dispatch_service.messaging;

import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component @ConditionalOnProperty(name="dispatch.messaging-enabled",havingValue="true",matchIfMissing=true)
public class ParcelListener {
    private final ParcelProjector projector;
    private final JsonMapper mapper;
    public ParcelListener(ParcelProjector projector, JsonMapper mapper) { this.projector=projector; this.mapper=mapper; }
    @RabbitListener(queues=Queues.DISPATCH_PARCEL_DISPATCH_CHANGED)
    public void receive(Message message) {
        var event = mapper.readValue(message.getBody(),new TypeReference<EventEnvelope<ParcelDispatchChangedV1>>() {});
        projector.apply(event);
    }
}

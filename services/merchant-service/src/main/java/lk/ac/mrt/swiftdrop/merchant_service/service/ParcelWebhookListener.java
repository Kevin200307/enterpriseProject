package lk.ac.mrt.swiftdrop.merchant_service.service;

import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ParcelWebhookListener {
    private final WebhookService webhooks;
    public ParcelWebhookListener(WebhookService webhooks){this.webhooks=webhooks;}
    @RabbitListener(queues=Queues.MERCHANT_WEBHOOK_PARCEL_STATUS_CHANGED_V2)
    public void receive(EventEnvelope<ParcelStatusChangedV2> event){webhooks.accept(event);}
}

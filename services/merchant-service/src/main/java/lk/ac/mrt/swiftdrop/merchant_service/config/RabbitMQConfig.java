package lk.ac.mrt.swiftdrop.merchant_service.config;

import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitMQConfig {
    @Bean TopicExchange parcelEventsExchange(){ return new TopicExchange(Exchanges.PARCEL_EVENTS,true,false); }
    @Bean Queue merchantWebhookQueue(){ return QueueBuilder.durable(Queues.MERCHANT_WEBHOOK_PARCEL_STATUS_CHANGED_V2).build(); }
    @Bean Binding merchantWebhookBinding(Queue merchantWebhookQueue, TopicExchange parcelEventsExchange){ return BindingBuilder.bind(merchantWebhookQueue).to(parcelEventsExchange).with(RoutingKeys.PARCEL_STATUS_CHANGED_V2); }
}

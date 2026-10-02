package lk.ac.mrt.swiftdrop.notification_service.config;

import lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    // exchanges
    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(Exchanges.PARCEL_EVENTS, true, false);
    }

    // delare the queues
    @Bean
    public Queue notificationDeadLetterQueue() {
        String dlqName = Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED);
        return QueueBuilder.durable(dlqName).build();
    }

    // set the event to Dead Letter Queue (DLQ) if it was failed
    @Bean
    public Queue notificationQueue() {
        String dlqName = Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED);
        return QueueBuilder.durable(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED)
                .deadLetterExchange("")
                .deadLetterRoutingKey(dlqName)
                .build();
    }

    // bind the queue to the exchange with the routing key
    @Bean
    public Binding notificationBinding(Queue notificationQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_STATUS_CHANGED_V1);
    }

    // use json message converter
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
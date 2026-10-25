package lk.ac.mrt.swiftdrop.tracking_service.config;

import lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TRACKING_QUEUE = Queues.TRACKING_PARCEL_STATUS_CHANGED;
    public static final String TRACKING_DLQ = Queues.deadLetterOf(TRACKING_QUEUE);

    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(Exchanges.PARCEL_EVENTS, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(Exchanges.DEAD_LETTER, true, false);
    }

    @Bean
    public Queue trackingDlq() {
        return QueueBuilder.durable(TRACKING_DLQ).build();
    }

    @Bean
    public Binding trackingDlqBinding(Queue trackingDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(trackingDlq)
                .to(deadLetterExchange)
                .with(TRACKING_DLQ);
    }

    @Bean
    public Queue trackingQueue() {
        return QueueBuilder.durable(TRACKING_QUEUE)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(TRACKING_DLQ)
                .build();
    }

    @Bean
    public Binding trackingStatusChangedBinding(Queue trackingQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(trackingQueue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_STATUS_CHANGED_V1);
    }

    @Bean
    public Binding trackingSnapshotBinding(Queue trackingQueue, TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(trackingQueue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_TRACKING_SNAPSHOT_V1);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

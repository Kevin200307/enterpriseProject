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
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String TRACKING_QUEUE = Queues.TRACKING_PARCEL_STATUS_CHANGED;
    public static final String TRACKING_DLQ = Queues.deadLetterOf(TRACKING_QUEUE);
    public static final String TRACKING_V2_QUEUE = Queues.TRACKING_PARCEL_STATUS_CHANGED_V2;
    public static final String TRACKING_V2_DLQ = Queues.deadLetterOf(TRACKING_V2_QUEUE);
    public static final String TRACKING_SNAPSHOT_QUEUE = Queues.TRACKING_PARCEL_SNAPSHOT;
    public static final String TRACKING_SNAPSHOT_DLQ = Queues.deadLetterOf(TRACKING_SNAPSHOT_QUEUE);

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
    public Binding trackingDlqBinding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingDlq") Queue trackingDlq,
            DirectExchange deadLetterExchange) {
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
    public Binding trackingStatusChangedBinding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingQueue") Queue trackingQueue,
            TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(trackingQueue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_STATUS_CHANGED_V1);
    }

    @Bean
    public Queue trackingStatusChangedV2Queue() {
        return QueueBuilder.durable(TRACKING_V2_QUEUE)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(TRACKING_V2_DLQ)
                .build();
    }

    @Bean
    public Queue trackingStatusChangedV2Dlq() {
        return QueueBuilder.durable(TRACKING_V2_DLQ).build();
    }

    @Bean
    public Binding trackingStatusChangedV2DlqBinding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingStatusChangedV2Dlq")
            Queue trackingStatusChangedV2Dlq,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(trackingStatusChangedV2Dlq)
                .to(deadLetterExchange)
                .with(TRACKING_V2_DLQ);
    }

    @Bean
    public Binding trackingStatusChangedV2Binding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingStatusChangedV2Queue")
            Queue trackingStatusChangedV2Queue,
            TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(trackingStatusChangedV2Queue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_STATUS_CHANGED_V2);
    }

    @Bean
    public Binding trackingSnapshotBinding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingSnapshotQueue")
            Queue trackingSnapshotQueue,
            TopicExchange parcelEventsExchange) {
        return BindingBuilder.bind(trackingSnapshotQueue)
                .to(parcelEventsExchange)
                .with(RoutingKeys.PARCEL_TRACKING_SNAPSHOT_V1);
    }

    @Bean
    public Queue trackingSnapshotQueue() {
        return QueueBuilder.durable(TRACKING_SNAPSHOT_QUEUE)
                .deadLetterExchange(Exchanges.DEAD_LETTER)
                .deadLetterRoutingKey(TRACKING_SNAPSHOT_DLQ)
                .build();
    }

    @Bean
    public Queue trackingSnapshotDlq() {
        return QueueBuilder.durable(TRACKING_SNAPSHOT_DLQ).build();
    }

    @Bean
    public Binding trackingSnapshotDlqBinding(
            @org.springframework.beans.factory.annotation.Qualifier("trackingSnapshotDlq")
            Queue trackingSnapshotDlq,
            DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(trackingSnapshotDlq)
                .to(deadLetterExchange)
                .with(TRACKING_SNAPSHOT_DLQ);
    }

    @Bean
    public MessageConverter messageConverter(tools.jackson.databind.json.JsonMapper mapper) {
        return new JacksonJsonMessageConverter(mapper);
    }
}

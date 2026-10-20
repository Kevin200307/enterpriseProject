package lk.ac.mrt.swiftdrop.notification_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;

class RabbitMQConfigTest {

    private RabbitMQConfig rabbitMQConfig;

    @BeforeEach
    void setUp() {
        rabbitMQConfig = new RabbitMQConfig();
    }

    @Test
    @DisplayName("Should configure parcel events topic exchange with correct name and durability")
    void shouldConfigureParcelEventsTopicExchange() {
        TopicExchange exchange = rabbitMQConfig.parcelEventsExchange();

        assertNotNull(exchange);
        assertEquals(Exchanges.PARCEL_EVENTS, exchange.getName());
        assertTrue(exchange.isDurable());
    }

    @Test
    @DisplayName("Should configure notification queue with dead letter exchange routing")
    void shouldConfigureNotificationQueue() {
        Queue queue = rabbitMQConfig.notificationQueue();

        assertNotNull(queue);
        assertEquals(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED, queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED),
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("Should configure dead letter queue with durable flag")
    void shouldConfigureDeadLetterQueue() {
        Queue dlq = rabbitMQConfig.notificationDeadLetterQueue();

        assertNotNull(dlq);
        assertEquals(Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED), dlq.getName());
        assertTrue(dlq.isDurable());
    }

    @Test
    @DisplayName("Should bind notification queue to topic exchange with routing key")
    void shouldBindNotificationQueue() {
        Queue queue = rabbitMQConfig.notificationQueue();
        TopicExchange exchange = rabbitMQConfig.parcelEventsExchange();

        Binding binding = rabbitMQConfig.notificationBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED, binding.getDestination());
        assertEquals(Exchanges.PARCEL_EVENTS, binding.getExchange());
        assertEquals(RoutingKeys.PARCEL_STATUS_CHANGED_V1, binding.getRoutingKey());
    }

    @Test
    @DisplayName("Should configure Jackson2JsonMessageConverter bean")
    void shouldConfigureJsonMessageConverter() {
        MessageConverter converter = rabbitMQConfig.jsonMessageConverter();

        assertNotNull(converter);
        assertTrue(converter instanceof Jackson2JsonMessageConverter);
    }
}

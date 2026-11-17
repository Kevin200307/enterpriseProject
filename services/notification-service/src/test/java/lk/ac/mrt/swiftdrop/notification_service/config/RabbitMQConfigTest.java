package lk.ac.mrt.swiftdrop.notification_service.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import tools.jackson.databind.json.JsonMapper;

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
        assertEquals(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2, queue.getName());
        assertTrue(queue.isDurable());
        assertEquals("", queue.getArguments().get("x-dead-letter-exchange"));
        assertEquals(Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2),
                queue.getArguments().get("x-dead-letter-routing-key"));
    }

    @Test
    @DisplayName("Should configure dead letter queue with durable flag")
    void shouldConfigureDeadLetterQueue() {
        Queue dlq = rabbitMQConfig.notificationDeadLetterQueue();

        assertNotNull(dlq);
        assertEquals(Queues.deadLetterOf(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2), dlq.getName());
        assertTrue(dlq.isDurable());
    }

    @Test
    @DisplayName("Should bind notification queue to topic exchange with routing key")
    void shouldBindNotificationQueue() {
        Queue queue = rabbitMQConfig.notificationQueue();
        TopicExchange exchange = rabbitMQConfig.parcelEventsExchange();

        Binding binding = rabbitMQConfig.notificationBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals(Queues.NOTIFICATION_PARCEL_STATUS_CHANGED_V2, binding.getDestination());
        assertEquals(Exchanges.PARCEL_EVENTS, binding.getExchange());
        assertEquals(RoutingKeys.PARCEL_STATUS_CHANGED_V2, binding.getRoutingKey());
    }

    @Test
    @DisplayName("Should decode parcel event timestamps and typed payloads without type headers")
    void shouldDecodeParcelEvent() {
        MessageConverter converter = rabbitMQConfig.jsonMessageConverter(JsonMapper.builder().build());
        String json = """
                {
                  "eventId": "3f1c2b9e-8a4d-4c6e-9b1a-2d7e5f0c1a23",
                  "eventType": "parcel.status.changed",
                  "version": 2,
                  "occurredAt": "2026-09-29T10:15:30Z",
                  "producer": "parcel-service",
                  "payload": {
                    "parcelId": "b6a4e1d2-5c3f-4a8b-9e7d-1f2a3b4c5d6e",
                    "trackingNumber": "SD-4F7K2Q9M",
                    "customerUserId": "11111111-1111-1111-1111-111111111111",
                    "merchantId": null,
                    "assigneeUserId": null,
                    "fromStatus": null,
                    "toStatus": "CREATED",
                    "changedByUserId": "11111111-1111-1111-1111-111111111111",
                    "failureReason": null,
                    "proofId": null
                  }
                }
                """;
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(new ParameterizedTypeReference<EventEnvelope<ParcelStatusChangedV2>>() {}.getType());
        Message message = new Message(json.getBytes(StandardCharsets.UTF_8), properties);

        EventEnvelope<?> envelope = assertInstanceOf(EventEnvelope.class, converter.fromMessage(message));
        assertEquals(Instant.parse("2026-09-29T10:15:30Z"), envelope.occurredAt());
        ParcelStatusChangedV2 payload = assertInstanceOf(ParcelStatusChangedV2.class, envelope.payload());
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), payload.customerUserId());
        assertEquals(ParcelStatus.CREATED, payload.toStatus());
    }
}

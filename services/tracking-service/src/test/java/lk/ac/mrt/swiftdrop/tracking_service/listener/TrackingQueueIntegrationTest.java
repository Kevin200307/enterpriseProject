package lk.ac.mrt.swiftdrop.tracking_service.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.messaging.Exchanges;
import lk.ac.mrt.swiftdrop.contracts.messaging.Queues;
import lk.ac.mrt.swiftdrop.contracts.messaging.RoutingKeys;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrackingQueueIntegrationTest {

    @Container
    private static final RabbitMQContainer RABBIT = new RabbitMQContainer(
            DockerImageName.parse("rabbitmq:3-management-alpine"));

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @MockitoSpyBean
    private TrackingProjectionService projectionService;

    @Test
    void repeatedDeliveryProducesOneProcessing() {
        UUID eventId = UUID.randomUUID();
        String tn = "SD-4F7K2Q9M";
        ParcelStatusChangedV1 payload = new ParcelStatusChangedV1(
                UUID.randomUUID(), tn, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null);
        EventEnvelope<ParcelStatusChangedV1> envelope = new EventEnvelope<>(
                eventId, ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION,
                java.time.Instant.now(), "parcel-service", payload);

        // Send 5 times
        for (int i = 0; i < 5; i++) {
            rabbitTemplate.convertAndSend(Exchanges.PARCEL_EVENTS, RoutingKeys.PARCEL_STATUS_CHANGED_V1, envelope);
        }

        // Wait for processing
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(projectionService, times(1)).applyStatusChanged(any());
        });
    }

    @Test
    void poisonMessageReachesDlqAndConsumerRecovers() {
        // Send a poison message (raw invalid payload that fails or message targeting fail condition)
        String dlqName = Queues.deadLetterOf(Queues.TRACKING_PARCEL_STATUS_CHANGED);

        // Purge DLQ first
        while (rabbitTemplate.receive(dlqName, 500) != null) {
            // purge
        }

        // Send raw poison text message that fails deserialization to EventEnvelope
        rabbitTemplate.send(Exchanges.PARCEL_EVENTS, RoutingKeys.PARCEL_STATUS_CHANGED_V1,
                new Message("{\"corrupted\":\"poison\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        // Expect it to reach DLQ after retry attempts
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Message dlqMessage = rabbitTemplate.receive(dlqName, 2000);
            assertThat(dlqMessage).isNotNull();
        });

        // Verify consumer is healthy and processes a subsequent valid message
        UUID validEventId = UUID.randomUUID();
        ParcelStatusChangedV1 validPayload = new ParcelStatusChangedV1(
                UUID.randomUUID(), "SD-VALIDTN1", ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT, null, null);
        EventEnvelope<ParcelStatusChangedV1> validEnvelope = new EventEnvelope<>(
                validEventId, ParcelStatusChangedV1.EVENT_TYPE, ParcelStatusChangedV1.VERSION,
                java.time.Instant.now(), "parcel-service", validPayload);

        rabbitTemplate.convertAndSend(Exchanges.PARCEL_EVENTS, RoutingKeys.PARCEL_STATUS_CHANGED_V1, validEnvelope);

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(projectionService, times(1)).applyStatusChanged(validEnvelope);
        });
    }
}

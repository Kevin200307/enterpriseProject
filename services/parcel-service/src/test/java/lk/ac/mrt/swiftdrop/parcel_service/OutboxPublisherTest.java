package lk.ac.mrt.swiftdrop.parcel_service;

import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.OutboxEvent;
import lk.ac.mrt.swiftdrop.parcel_service.messaging.OutboxPublisher;
import lk.ac.mrt.swiftdrop.parcel_service.repository.OutboxRepository;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    private OutboxRepository repository;
    private RabbitTemplate rabbit;
    private OutboxPublisher publisher;
    private OutboxEvent event;
    private final Instant now = Instant.parse("2026-10-15T00:00:00Z");
    @BeforeEach void setup() {
        repository = mock(OutboxRepository.class);
        rabbit = mock(RabbitTemplate.class);
        publisher = new OutboxPublisher(repository, rabbit, Clock.fixed(now, ZoneOffset.UTC));
        event = new OutboxEvent(UUID.randomUUID(), "{\"eventType\":\"parcel.status.changed\"}", now);
        when(repository.pending(any())).thenReturn(List.of(event));
    }
    private void acknowledge(boolean ack, boolean returned) {
        doAnswer(invocation -> {
            Message message = invocation.getArgument(2);
            assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
            assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
            CorrelationData data = invocation.getArgument(3);
            if (returned) data.setReturned(new ReturnedMessage(message,312,"NO_ROUTE",Exchanges.PARCEL_EVENTS,RoutingKeys.PARCEL_STATUS_CHANGED_V1));
            data.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbit).send(eq(Exchanges.PARCEL_EVENTS), eq(RoutingKeys.PARCEL_STATUS_CHANGED_V1), any(Message.class), any(CorrelationData.class));
    }
    @Test void marksPublishedOnlyAfterBrokerConfirmation() {
        acknowledge(true, false);
        publisher.publish();
        assertThat(event.getPublishedAt()).isEqualTo(now);
    }
    @Test void rejectedAndUnroutableEventsRemainPending() {
        acknowledge(false, false);
        publisher.publish();
        assertThat(event.getPublishedAt()).isNull();
        acknowledge(true, true);
        publisher.publish();
        assertThat(event.getPublishedAt()).isNull();
    }
    @Test void connectionFailuresRemainPending() {
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException()))
            .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publish();
        assertThat(event.getPublishedAt()).isNull();
    }
}

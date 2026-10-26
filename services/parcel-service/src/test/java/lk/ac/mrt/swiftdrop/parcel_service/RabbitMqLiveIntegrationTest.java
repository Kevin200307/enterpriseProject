package lk.ac.mrt.swiftdrop.parcel_service;

import java.time.*;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.OutboxEvent;
import lk.ac.mrt.swiftdrop.parcel_service.messaging.OutboxPublisher;
import lk.ac.mrt.swiftdrop.parcel_service.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="PARCEL_LIVE_BROKER_TESTS",matches="true")
class RabbitMqLiveIntegrationTest {
    @Test void publishesOneConfirmedJsonMessageAndRetainsAnUnroutableEvent() {
        var factory = new CachingConnectionFactory(System.getenv("RABBITMQ_HOST"),
            Integer.parseInt(System.getenv("RABBITMQ_PORT")));
        factory.setUsername(System.getenv("RABBITMQ_USERNAME"));
        factory.setPassword(System.getenv("RABBITMQ_PASSWORD"));
        factory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        var template = new RabbitTemplate(factory);
        template.setMandatory(true);
        var admin = new RabbitAdmin(factory);
        String queue = "parcel-acceptance-" + UUID.randomUUID();
        try {
            admin.declareExchange(new TopicExchange(Exchanges.PARCEL_EVENTS,true,false));
            admin.declareQueue(new org.springframework.amqp.core.Queue(queue,false,false,false));
            admin.declareBinding(new Binding(queue,Binding.DestinationType.QUEUE,Exchanges.PARCEL_EVENTS,
                RoutingKeys.PARCEL_STATUS_CHANGED_V1,null));
            var repository = mock(OutboxRepository.class);
            var event = new OutboxEvent(UUID.randomUUID(), "{\"eventType\":\"parcel.status.changed\"}", Instant.now());
            when(repository.pending(any())).thenReturn(List.of(event));
            var publisher = new OutboxPublisher(repository,template,Clock.systemUTC());
            publisher.publish();
            assertThat(event.getPublishedAt()).isNotNull();
            Message received = template.receive(queue,5000);
            assertThat(received).isNotNull();
            assertThat(received.getBody()).isEqualTo(event.getPayload().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertThat(received.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
            assertThat(template.receive(queue)).isNull();
            admin.deleteQueue(queue);
            var unroutable = new OutboxEvent(UUID.randomUUID(),"{}",Instant.now());
            when(repository.pending(any())).thenReturn(List.of(unroutable));
            publisher.publish();
            assertThat(unroutable.getPublishedAt()).isNull();
        } finally {
            admin.deleteQueue(queue);
            factory.destroy();
        }
    }
}

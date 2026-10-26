package lk.ac.mrt.swiftdrop.parcel_service.messaging;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import lk.ac.mrt.swiftdrop.parcel_service.repository.OutboxRepository;
import org.slf4j.*;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name="parcel.outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final Clock clock;

    @Scheduled(fixedDelayString="${parcel.outbox.delay-ms:3000}")
    @Transactional
    public void publish() {
        for (var event : outbox.pending(PageRequest.of(0, 20))) {
            try {
                var properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(event.getId().toString());
                var correlation = new CorrelationData(event.getId().toString());
                rabbit.send(Exchanges.PARCEL_EVENTS, RoutingKeys.PARCEL_STATUS_CHANGED_V1,
                    new Message(event.getPayload().getBytes(StandardCharsets.UTF_8), properties), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned() != null) {
                    LOG.warn("Parcel event {} was not accepted or routed; retaining for retry", event.getId());
                    break;
                }
                event.published(clock.instant());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                LOG.warn("Parcel event {} publish failed ({}); retaining for retry", event.getId(), ex.getClass().getSimpleName());
                break;
            }
        }
    }
    @Configuration
    @EnableScheduling
    static class Scheduling {
        @Bean TopicExchange parcelExchange() { return new TopicExchange(Exchanges.PARCEL_EVENTS, true, false); }
    }

    public OutboxPublisher(OutboxRepository outbox, RabbitTemplate rabbit, Clock clock) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.clock = clock;
    }
}

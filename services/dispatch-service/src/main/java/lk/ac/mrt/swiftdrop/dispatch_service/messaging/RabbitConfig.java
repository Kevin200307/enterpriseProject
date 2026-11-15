package lk.ac.mrt.swiftdrop.dispatch_service.messaging;

import lk.ac.mrt.swiftdrop.contracts.messaging.*;
import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;

@Configuration @ConditionalOnProperty(name="dispatch.messaging-enabled",havingValue="true",matchIfMissing=true)
public class RabbitConfig {
    @Bean org.springframework.amqp.rabbit.retry.MessageRecoverer dispatchMessageRecoverer() {
        return (message,cause) -> { throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Dispatch event exhausted retries"); };
    }
    @Bean org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            org.springframework.boot.amqp.autoconfigure.SimpleRabbitListenerContainerFactoryConfigurer configurer,
            org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        var factory = new org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory();
        configurer.configure(factory,connectionFactory);
        factory.setErrorHandler(error -> {
            // Spring's default handler/recoverer can print the entire failed message, including private addresses.
            org.slf4j.LoggerFactory.getLogger(RabbitConfig.class).warn("Dispatch event rejected after retry ({})",error.getClass().getSimpleName());
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Dispatch event failed");
        });
        return factory;
    }
    @Bean Declarables topology() {
        var exchange = new TopicExchange(Exchanges.PARCEL_EVENTS,true,false);
        var dlx = new DirectExchange(Exchanges.DEAD_LETTER,true,false);
        var name = Queues.DISPATCH_PARCEL_DISPATCH_CHANGED;
        var queue = QueueBuilder.durable(name).deadLetterExchange(Exchanges.DEAD_LETTER).deadLetterRoutingKey(name).build();
        var dlq = QueueBuilder.durable(Queues.deadLetterOf(name)).build();
        return new Declarables(exchange,dlx,queue,dlq,
                BindingBuilder.bind(queue).to(exchange).with(RoutingKeys.PARCEL_DISPATCH_CHANGED_V1),
                BindingBuilder.bind(dlq).to(dlx).with(name));
    }
}

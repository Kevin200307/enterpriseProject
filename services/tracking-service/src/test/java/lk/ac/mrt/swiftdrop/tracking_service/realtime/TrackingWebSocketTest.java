package lk.ac.mrt.swiftdrop.tracking_service.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingSubscriptionInterceptor;
import lk.ac.mrt.swiftdrop.tracking_service.config.WebSocketConfig;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
class TrackingWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TrackingRealtimePublisher realtimePublisher;

    @Autowired
    private TrackingSubscriptionInterceptor subscriptionInterceptor;

    @MockitoBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @MockitoBean
    private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
    }

    @Test
    void multiSubscriberDeliveryAndPrivacyGuarantee() throws Exception {
        String tn = "SD-4F7K2Q9M";
        String wsUrl = "http://localhost:" + port + "/ws";

        BlockingQueue<PublicTracking> queue1 = new LinkedBlockingQueue<>();
        BlockingQueue<PublicTracking> queue2 = new LinkedBlockingQueue<>();

        StompSession session1 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        StompSession session2 = stompClient.connectAsync(wsUrl, new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);

        session1.subscribe("/topic/parcels." + tn, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return PublicTracking.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue1.offer((PublicTracking) payload);
            }
        });

        session2.subscribe("/topic/parcels." + tn, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return PublicTracking.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue2.offer((PublicTracking) payload);
            }
        });

        // Small wait to ensure subscriptions are established on broker
        Thread.sleep(500);

        Instant now = Instant.parse("2026-10-15T10:30:00Z");
        PublicTracking update = new PublicTracking(
                tn,
                ParcelStatus.IN_TRANSIT,
                "Colombo",
                "Kandy",
                "Kasun",
                "2026-10-16T12:00:00Z",
                now,
                List.of(new TimelineEntry(ParcelStatus.IN_TRANSIT, now, "Left Colombo hub"))
        );

        // Publish masked update
        realtimePublisher.publishUpdate(update);

        // Both subscribers receive the same status update within one second
        PublicTracking received1 = queue1.poll(1, TimeUnit.SECONDS);
        PublicTracking received2 = queue2.poll(1, TimeUnit.SECONDS);

        assertThat(received1).isNotNull();
        assertThat(received2).isNotNull();
        assertThat(received1.trackingNumber()).isEqualTo(tn);
        assertThat(received2.trackingNumber()).isEqualTo(tn);
        assertThat(received1.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(received2.status()).isEqualTo(ParcelStatus.IN_TRANSIT);

        // Verify privacy: no raw UUIDs or forbidden PII in payload
        assertNoPiiInPublicTracking(received1);
        assertNoPiiInPublicTracking(received2);

        session1.disconnect();
        session2.disconnect();
    }

    @Test
    void rejectsInvalidSubscriptionDestinationAndEnforcesCap() {
        // 1. Invalid destination
        StompHeaderAccessor invalidDest = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        invalidDest.setDestination("/topic/other.channel");
        invalidDest.setSessionId("sess-test-1");
        Message<?> invalidMsg = MessageBuilder.createMessage(new byte[0], invalidDest.getMessageHeaders());

        assertThatThrownBy(() -> subscriptionInterceptor.preSend(invalidMsg, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unauthorized subscription destination");

        // 2. Malformed tracking number
        StompHeaderAccessor badTnDest = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        badTnDest.setDestination("/topic/parcels.bad-tracking-number");
        badTnDest.setSessionId("sess-test-1");
        Message<?> badTnMsg = MessageBuilder.createMessage(new byte[0], badTnDest.getMessageHeaders());

        assertThatThrownBy(() -> subscriptionInterceptor.preSend(badTnMsg, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Malformed tracking number");

        // 3. Subscription cap enforcement
        String capSessionId = "sess-cap-test";
        for (int i = 0; i < TrackingSubscriptionInterceptor.MAX_SUBSCRIPTIONS_PER_SESSION; i++) {
            StompHeaderAccessor okAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
            okAccessor.setDestination("/topic/parcels.SD-4F7K2Q9M");
            okAccessor.setSubscriptionId("sub-" + i);
            okAccessor.setSessionId(capSessionId);
            Message<?> okMsg = MessageBuilder.createMessage(new byte[0], okAccessor.getMessageHeaders());
            subscriptionInterceptor.preSend(okMsg, null);
        }

        // 11th subscription must throw IllegalStateException
        StompHeaderAccessor excessAccessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        excessAccessor.setDestination("/topic/parcels.SD-4F7K2Q9M");
        excessAccessor.setSubscriptionId("sub-excess");
        excessAccessor.setSessionId(capSessionId);
        Message<?> excessMsg = MessageBuilder.createMessage(new byte[0], excessAccessor.getMessageHeaders());

        assertThatThrownBy(() -> subscriptionInterceptor.preSend(excessMsg, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Maximum active subscriptions exceeded");
    }

    private void assertNoPiiInPublicTracking(PublicTracking tracking) {
        assertThat(tracking.originCity()).isEqualTo("Colombo");
        assertThat(tracking.destinationCity()).isEqualTo("Kandy");
        assertThat(tracking.courierFirstName()).isEqualTo("Kasun");
        assertThat(UUID_REGEX.matcher(tracking.trackingNumber()).matches()).isFalse();
        assertThat(UUID_REGEX.matcher(tracking.originCity()).matches()).isFalse();
        assertThat(UUID_REGEX.matcher(tracking.destinationCity()).matches()).isFalse();
    }
}

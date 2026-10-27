package lk.ac.mrt.swiftdrop.tracking_service.config;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Channel interceptor to validate subscription destinations and cap subscriptions per session.
 */
@Slf4j
@Component
public class TrackingSubscriptionInterceptor implements ChannelInterceptor {

    public static final int MAX_SUBSCRIPTIONS_PER_SESSION = 10;
    private static final Pattern TOPIC_PATTERN = Pattern.compile("^/topic/parcels\\.([A-Za-z0-9-]+)$");

    private final Map<String, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();

        if (StompCommand.SUBSCRIBE.equals(command)) {
            String destination = accessor.getDestination();
            if (destination == null) {
                throw new IllegalArgumentException("Subscription destination cannot be null");
            }

            Matcher matcher = TOPIC_PATTERN.matcher(destination);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Unauthorized subscription destination: " + destination);
            }

            String trackingNumber = matcher.group(1);
            if (!TrackingNumber.isValid(trackingNumber)) {
                throw new IllegalArgumentException("Malformed tracking number in subscription destination: " + destination);
            }

            if (sessionId != null) {
                Set<String> subs = sessionSubscriptions.computeIfAbsent(
                        sessionId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                if (subs.size() >= MAX_SUBSCRIPTIONS_PER_SESSION) {
                    log.warn("Session {} exceeded subscription limit of {}", sessionId, MAX_SUBSCRIPTIONS_PER_SESSION);
                    throw new IllegalStateException("Maximum active subscriptions exceeded for session");
                }
                String subId = accessor.getSubscriptionId() != null ? accessor.getSubscriptionId() : destination;
                subs.add(subId);
            }
        } else if (StompCommand.UNSUBSCRIBE.equals(command)) {
            if (sessionId != null) {
                Set<String> subs = sessionSubscriptions.get(sessionId);
                if (subs != null && accessor.getSubscriptionId() != null) {
                    subs.remove(accessor.getSubscriptionId());
                }
            }
        } else if (StompCommand.DISCONNECT.equals(command)) {
            if (sessionId != null) {
                sessionSubscriptions.remove(sessionId);
            }
        }

        return message;
    }

    public Map<String, Set<String>> getSessionSubscriptions() {
        return sessionSubscriptions;
    }
}

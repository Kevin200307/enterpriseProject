package lk.ac.mrt.swiftdrop.notification_service.channel;

import java.util.List;
import java.util.Optional;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Open-Closed Principle Router that routes notifications to appropriate channel handlers.
 * 
 * <p>Spring injects all available {@link NotificationChannelHandler} implementations
 * into this router automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelRouter {

    private final List<NotificationChannelHandler> channelHandlers;

    /**
     * Resolves the appropriate channel handler for the specified notification channel.
     */
    public Optional<NotificationChannelHandler> getHandler(NotificationChannel channel) {
        return channelHandlers.stream()
                .filter(handler -> handler.supports(channel))
                .findFirst();
    }

    /**
     * Dispatches the notification request through the matching channel handler.
     */
    public void route(NotificationChannel channel, NotificationChannelHandler.NotificationRequest request) {
        NotificationChannelHandler handler = getHandler(channel)
                .orElseThrow(() -> new IllegalArgumentException("No handler configured for channel: " + channel));
        
        handler.send(request);
    }
}

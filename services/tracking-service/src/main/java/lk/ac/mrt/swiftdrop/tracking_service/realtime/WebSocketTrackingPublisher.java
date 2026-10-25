package lk.ac.mrt.swiftdrop.tracking_service.realtime;

import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketTrackingPublisher implements TrackingRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final TrackingSseService sseService;

    @Override
    public void publishUpdate(PublicTracking update) {
        if (update == null || update.trackingNumber() == null) {
            return;
        }

        // 1. WebSocket / STOMP broadcast
        String destination = "/topic/parcels." + update.trackingNumber();
        log.debug("Broadcasting tracking update to WebSocket destination {}", destination);
        messagingTemplate.convertAndSend(destination, update);

        // 2. Server-Sent Events broadcast
        log.debug("Broadcasting tracking update to SSE subscribers for {}", update.trackingNumber());
        sseService.broadcast(update);
    }
}

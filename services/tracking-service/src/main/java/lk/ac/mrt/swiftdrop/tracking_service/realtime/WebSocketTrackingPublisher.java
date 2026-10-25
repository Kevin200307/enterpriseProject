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

    @Override
    public void publishUpdate(PublicTracking update) {
        if (update == null || update.trackingNumber() == null) {
            return;
        }
        String destination = "/topic/parcels." + update.trackingNumber();
        log.debug("Broadcasting tracking update to WebSocket destination {}", destination);
        messagingTemplate.convertAndSend(destination, update);
    }
}

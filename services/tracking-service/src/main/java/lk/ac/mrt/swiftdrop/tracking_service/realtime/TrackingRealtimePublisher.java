package lk.ac.mrt.swiftdrop.tracking_service.realtime;

import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;

/**
 * Publishes masked real-time updates to WebSocket and SSE subscribers.
 */
public interface TrackingRealtimePublisher {

    void publishUpdate(PublicTracking update);
}

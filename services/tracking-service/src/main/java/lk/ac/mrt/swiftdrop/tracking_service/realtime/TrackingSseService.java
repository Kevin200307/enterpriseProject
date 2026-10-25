package lk.ac.mrt.swiftdrop.tracking_service.realtime;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingException;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingSseService {

    public static final long SSE_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes maximum duration
    public static final int MAX_EMITTERS_PER_TRACKING_NUMBER = 50;

    private final TrackingProjectionService projectionService;

    // Per-tracking-number registry of active emitters
    private final Map<String, Set<SseEmitter>> activeSinks = new ConcurrentHashMap<>();

    public SseEmitter createEmitter(String trackingNumber) {
        if (!TrackingNumber.isValid(trackingNumber)) {
            throw new TrackingException(ErrorCode.VALIDATION_FAILED, "Malformed tracking number: " + trackingNumber);
        }

        TrackingProjection projection = projectionService.getProjection(trackingNumber)
                .orElseThrow(() -> new TrackingException(ErrorCode.RESOURCE_NOT_FOUND, "Tracking number was not found"));

        Set<SseEmitter> emitters = activeSinks.computeIfAbsent(
                trackingNumber, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));

        if (emitters.size() >= MAX_EMITTERS_PER_TRACKING_NUMBER) {
            log.warn("Exceeded max SSE emitters ({}) for tracking number {}", MAX_EMITTERS_PER_TRACKING_NUMBER, trackingNumber);
            throw new TrackingException(ErrorCode.CONFLICT, "Connection limit exceeded for tracking stream");
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> removeEmitter(trackingNumber, emitter));
        emitter.onTimeout(() -> {
            log.debug("SSE timeout for tracking number {}", trackingNumber);
            emitter.complete();
            removeEmitter(trackingNumber, emitter);
        });
        emitter.onError(ex -> {
            log.debug("SSE error for tracking number {}: {}", trackingNumber, ex.getMessage());
            emitter.complete();
            removeEmitter(trackingNumber, emitter);
        });

        // Send latest projection immediately on connection
        try {
            emitter.send(SseEmitter.event()
                    .data(projection.toPublicDto(), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            log.debug("Failed to send initial SSE projection to client for {}: {}", trackingNumber, ex.getMessage());
            emitter.completeWithError(ex);
            removeEmitter(trackingNumber, emitter);
        }

        return emitter;
    }

    public void broadcast(PublicTracking update) {
        if (update == null || update.trackingNumber() == null) {
            return;
        }

        String tn = update.trackingNumber();
        Set<SseEmitter> emitters = activeSinks.get(tn);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .data(update, MediaType.APPLICATION_JSON));
            } catch (Exception ex) {
                log.debug("Failed to stream SSE update to client for {}: {}", tn, ex.getMessage());
                emitter.completeWithError(ex);
                removeEmitter(tn, emitter);
            }
        }
    }

    /**
     * 20-second heartbeat ping to prevent gateway and proxy timeouts.
     */
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeats() {
        if (activeSinks.isEmpty()) {
            return;
        }

        activeSinks.forEach((tn, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception ex) {
                    log.debug("Evicting dead SSE client during heartbeat for {}: {}", tn, ex.getMessage());
                    emitter.complete();
                    removeEmitter(tn, emitter);
                }
            }
        });
    }

    public void removeEmitter(String trackingNumber, SseEmitter emitter) {
        Set<SseEmitter> emitters = activeSinks.get(trackingNumber);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                activeSinks.remove(trackingNumber, Collections.emptySet());
            }
        }
    }

    public Map<String, Set<SseEmitter>> getActiveSinks() {
        return activeSinks;
    }
}

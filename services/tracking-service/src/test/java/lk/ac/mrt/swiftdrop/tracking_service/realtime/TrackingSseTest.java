package lk.ac.mrt.swiftdrop.tracking_service.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.tracking_service.api.TrackingController;
import lk.ac.mrt.swiftdrop.tracking_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingException;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingExceptionHandler;
import lk.ac.mrt.swiftdrop.tracking_service.service.PrivateTrackingService;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@WebMvcTest(controllers = TrackingController.class)
@Import({SecurityConfig.class, TrackingExceptionHandler.class, TrackingSseService.class})
@EnableConfigurationProperties(TrackingProperties.class)
class TrackingSseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingSseService sseService;

    @MockitoBean
    private TrackingProjectionService projectionService;

    @MockitoBean
    private PrivateTrackingService privateTrackingService;

    private final String trackingNumber = "SD-4F7K2Q9M";
    private final Instant now = Instant.parse("2026-10-15T10:30:00Z");

    @BeforeEach
    void setUp() {
        TrackingProjection projection = new TrackingProjection(
                trackingNumber,
                ParcelStatus.IN_TRANSIT,
                "Colombo",
                "Kandy",
                "Kasun",
                "2026-10-16T12:00:00Z",
                now,
                List.of(new TimelineEntry(ParcelStatus.IN_TRANSIT, now, "Left Colombo hub"))
        );
        when(projectionService.getProjection(trackingNumber)).thenReturn(Optional.of(projection));
    }

    @Test
    void streamsSseWithGatewayNoBufferingHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}/stream", trackingNumber)
                        .header("X-Request-Id", "req-sse-1")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-cache")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-transform")))
                .andExpect(header().string("X-Request-Id", "req-sse-1"));
    }

    @Test
    void aliasStreamRouteWorksWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}/stream", trackingNumber)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test
    void rejectsMalformedTrackingNumberWith400() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}/stream", "bad-tn")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsFlat404WhenTrackingNumberNotFound() throws Exception {
        String unknown = "SD-99999999";
        when(projectionService.getProjection(unknown)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/public/{trackingNumber}/stream", unknown)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isNotFound());
    }

    @Test
    void serviceHandlesInitialPushHeartbeatBroadcastAndCleanup() {
        // Test SseService lifecycle directly
        SseEmitter emitter = sseService.createEmitter(trackingNumber);
        assertThat(emitter).isNotNull();
        assertThat(sseService.getActiveSinks().get(trackingNumber)).contains(emitter);

        // Broadcast future masked update
        PublicTracking update = new PublicTracking(
                trackingNumber,
                ParcelStatus.OUT_FOR_DELIVERY,
                "Colombo",
                "Kandy",
                "Kasun",
                "2026-10-16T14:00:00Z",
                now.plusSeconds(3600),
                List.of(new TimelineEntry(ParcelStatus.OUT_FOR_DELIVERY, now.plusSeconds(3600), "Out for delivery"))
        );
        sseService.broadcast(update);

        // Trigger heartbeat
        sseService.sendHeartbeats();

        // Disconnect cleanup
        sseService.removeEmitter(trackingNumber, emitter);
        assertThat(sseService.getActiveSinks().getOrDefault(trackingNumber, java.util.Collections.emptySet()))
                .doesNotContain(emitter);
    }
}

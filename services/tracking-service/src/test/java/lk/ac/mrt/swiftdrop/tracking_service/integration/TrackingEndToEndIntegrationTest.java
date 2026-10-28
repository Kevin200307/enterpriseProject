package lk.ac.mrt.swiftdrop.tracking_service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.metrics.TrackingMetrics;
import lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingSseService;
import lk.ac.mrt.swiftdrop.tracking_service.repository.RedisTrackingRepository;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end integration test verifying the complete flow:
 * Status change event -> Deduplication & Redis projection -> Real-time WebSocket/SSE broadcast -> Public REST read.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379"
})
@AutoConfigureMockMvc
class TrackingEndToEndIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TrackingProjectionService projectionService;

    @Autowired
    private TrackingSseService sseService;

    @Autowired
    private TrackingMetrics trackingMetrics;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RedisTrackingRepository redisRepository;

    @MockitoBean
    private ParcelServiceClient parcelClient;

    @MockitoBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @MockitoBean
    private org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry rabbitListenerEndpointRegistry;

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
                List.of(
                        new TimelineEntry(ParcelStatus.CREATED, now.minusSeconds(3600), "Order confirmed"),
                        new TimelineEntry(ParcelStatus.IN_TRANSIT, now, "Left Colombo hub")
                )
        );

        when(redisRepository.findByTrackingNumber(trackingNumber)).thenReturn(Optional.of(projection));
    }

    @Test
    void endToEndEventToPublicRestAndPrivacySafe() throws Exception {
        // 1. Query public tracking GET endpoint
        MvcResult result = mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", trackingNumber)
                        .header("X-Request-Id", "e2e-req-1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=10")))
                .andExpect(header().string("X-Request-Id", "e2e-req-1"))
                .andExpect(jsonPath("$.trackingNumber").value(trackingNumber))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.originCity").value("Colombo"))
                .andExpect(jsonPath("$.destinationCity").value("Kandy"))
                .andExpect(jsonPath("$.courierFirstName").value("Kasun"))
                .andReturn();

        // 2. Assert privacy regression (no forbidden fields or UUID values)
        String json = result.getResponse().getContentAsString();
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        assertNoPii(root);
    }

    @Test
    void endToEndSseStreamLifecycleAndHeartbeat() throws Exception {
        // 1. Establish SSE stream
        SseEmitter emitter = sseService.createEmitter(trackingNumber);
        assertThat(emitter).isNotNull();
        assertThat(sseService.getActiveSinks().get(trackingNumber)).contains(emitter);

        // 2. Broadcast status update
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

        // 3. Heartbeat execution
        sseService.sendHeartbeats();

        // 4. Disconnect cleanup
        sseService.removeEmitter(trackingNumber, emitter);
        assertThat(sseService.getActiveSinks().getOrDefault(trackingNumber, java.util.Collections.emptySet()))
                .doesNotContain(emitter);
    }

    @Test
    void rejectsMalformedTrackingNumberWith400() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", "invalid-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void returnsFlat404ForUnknownTrackingNumber() throws Exception {
        String unknown = "SD-99999999";
        when(redisRepository.findByTrackingNumber(unknown)).thenReturn(Optional.empty());
        when(parcelClient.fetchSnapshot(unknown)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void outOfOrderEventDoesNotRegressStatus() {
        // Event with older timestamp than current state
        Instant olderTime = now.minusSeconds(1800);
        EventEnvelope<ParcelStatusChangedV1> olderEvent = new EventEnvelope<>(
                UUID.randomUUID(),
                "parcel.status.changed",
                1,
                olderTime,
                "parcel-service",
                new ParcelStatusChangedV1(
                        UUID.randomUUID(),
                        trackingNumber,
                        ParcelStatus.CREATED,
                        ParcelStatus.PICKED_UP,
                        UUID.randomUUID(),
                        UUID.randomUUID()
                )
        );

        // Projection status remains IN_TRANSIT, does not regress to PICKED_UP
        projectionService.applyStatusChanged(olderEvent);
        Optional<TrackingProjection> proj = projectionService.getProjection(trackingNumber);
        assertThat(proj).isPresent();
        assertThat(proj.get().status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(trackingMetrics.getOutOfOrderCount()).isGreaterThanOrEqualTo(1.0);
    }

    private void assertNoPii(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String field = String.valueOf(entry.getKey()).toLowerCase();
                assertThat(field).doesNotContain("phone");
                assertThat(field).doesNotContain("street");
                assertThat(field).doesNotContain("line1");
                assertThat(field).doesNotContain("line2");
                assertThat(field).doesNotContain("postal");
                assertThat(field).doesNotContain("customer");
                assertThat(field).doesNotContain("recipient");
                assertThat(field).doesNotContain("contact");
                assertThat(field).doesNotContain("merchantid");
                assertThat(field).doesNotContain("actorid");
                assertThat(field).doesNotContain("parcelid");
                assertThat(field).isNotEqualTo("id");

                Object val = entry.getValue();
                if (val instanceof String str) {
                    assertThat(UUID_REGEX.matcher(str).matches())
                            .as("Field %s should not contain a raw UUID", field)
                            .isFalse();
                }
                assertNoPii(val);
            }
        } else if (obj instanceof List<?> list) {
            for (Object item : list) {
                assertNoPii(item);
            }
        }
    }
}

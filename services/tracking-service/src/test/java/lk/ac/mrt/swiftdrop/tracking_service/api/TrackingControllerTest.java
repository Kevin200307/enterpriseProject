package lk.ac.mrt.swiftdrop.tracking_service.api;

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
import java.util.regex.Pattern;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.tracking_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingExceptionHandler;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = TrackingController.class)
@Import({SecurityConfig.class, TrackingExceptionHandler.class})
@EnableConfigurationProperties(TrackingProperties.class)
class TrackingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrackingProjectionService projectionService;

    @MockitoBean
    private lk.ac.mrt.swiftdrop.tracking_service.service.PrivateTrackingService privateTrackingService;

    @MockitoBean
    private lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingSseService sseService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Pattern UUID_REGEX =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Test
    void returnsPublicTrackingWithoutAuthentication() throws Exception {
        String tn = "SD-4F7K2Q9M";
        Instant now = Instant.parse("2026-10-15T10:30:00Z");
        TrackingProjection projection = new TrackingProjection(
                tn,
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

        when(projectionService.getProjection(tn)).thenReturn(Optional.of(projection));

        MvcResult result = mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", tn)
                        .header("X-Request-Id", "req-12345")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=10")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andExpect(header().string("X-Request-Id", "req-12345"))
                .andExpect(jsonPath("$.trackingNumber").value(tn))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.originCity").value("Colombo"))
                .andExpect(jsonPath("$.destinationCity").value("Kandy"))
                .andExpect(jsonPath("$.courierFirstName").value("Kasun"))
                .andExpect(jsonPath("$.eta").value("2026-10-16T12:00:00Z"))
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline.length()").value(2))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        Map<String, Object> root = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        assertNoPii(root);
    }

    @Test
    void rejectsMalformedTrackingNumberWith400() throws Exception {
        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", "bad-tracking-number")
                        .header("X-Request-Id", "req-err-1"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "req-err-1"))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsFlat404ForUnknownTrackingNumber() throws Exception {
        String tn = "SD-99999999";
        when(projectionService.getProjection(tn)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/tracking/{trackingNumber}", tn))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private void assertNoPii(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String field = String.valueOf(entry.getKey()).toLowerCase();
                // Forbidden field names
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

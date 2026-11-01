package lk.ac.mrt.swiftdrop.tracking_service.listener;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class TrackingEventListenerTest {

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    @Mock
    private TrackingProjectionService projectionService;

    private TrackingProperties properties;
    private TrackingEventListener listener;

    @BeforeEach
    void setUp() {
        properties = new TrackingProperties(null, null, new TrackingProperties.CacheProperties(90, 90, 24));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        listener = new TrackingEventListener(redisTemplate, projectionService, properties);
    }

    @Test
    void processesNewEventAndMarksDedupKey() {
        UUID eventId = UUID.randomUUID();
        EventEnvelope<ParcelStatusChangedV1> envelope = new ParcelStatusChangedV1(
                UUID.randomUUID(), "SD-4F7K2Q9M", ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null)
                .toEnvelope("parcel-service");

        when(valueOperations.get("tracking:event:" + envelope.eventId())).thenReturn(Mono.empty());
        when(valueOperations.set(eq("tracking:event:" + envelope.eventId()), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        listener.onParcelStatusChanged(envelope);

        verify(projectionService, times(1)).applyStatusChanged(envelope);
        verify(valueOperations, times(1)).set(eq("tracking:event:" + envelope.eventId()), eq("PROCESSED"), eq(Duration.ofHours(24)));
    }

    @Test
    void dropsDuplicateEventWhenAlreadyProcessed() {
        EventEnvelope<ParcelStatusChangedV1> envelope = new ParcelStatusChangedV1(
                UUID.randomUUID(), "SD-4F7K2Q9M", ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null)
                .toEnvelope("parcel-service");

        when(valueOperations.get("tracking:event:" + envelope.eventId())).thenReturn(Mono.just("PROCESSED"));

        listener.onParcelStatusChanged(envelope);

        verify(projectionService, never()).applyStatusChanged(any());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void cleansUpDedupKeyOnProjectionFailureSoBrokerCanRetry() {
        EventEnvelope<ParcelStatusChangedV1> envelope = new ParcelStatusChangedV1(
                UUID.randomUUID(), "SD-4F7K2Q9M", ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null)
                .toEnvelope("parcel-service");

        when(valueOperations.get("tracking:event:" + envelope.eventId())).thenReturn(Mono.empty());
        when(redisTemplate.delete("tracking:event:" + envelope.eventId())).thenReturn(Mono.just(1L));

        org.mockito.Mockito.doThrow(new RuntimeException("Redis connection error"))
                .when(projectionService).applyStatusChanged(envelope);

        assertThrows(RuntimeException.class, () -> listener.onParcelStatusChanged(envelope));

        verify(redisTemplate, times(1)).delete("tracking:event:" + envelope.eventId());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void projectsV2StatusEventsAndMarksDedupKey() {
        var status = new ParcelStatusChangedV2(
                UUID.randomUUID(),
                "SD-4F7K2Q9M",
                UUID.randomUUID(),
                null,
                null,
                ParcelStatus.PICKED_UP,
                ParcelStatus.IN_TRANSIT,
                UUID.randomUUID(),
                null,
                null);
        EventEnvelope<ParcelStatusChangedV2> envelope = status.toEnvelope("parcel-service");

        when(valueOperations.get("tracking:event:" + envelope.eventId())).thenReturn(Mono.empty());
        when(valueOperations.set(eq("tracking:event:" + envelope.eventId()), eq("PROCESSED"), any(Duration.class)))
                .thenReturn(Mono.just(true));

        listener.onParcelStatusChangedV2(envelope);

        verify(projectionService).applyStatusChangedV2(envelope);
        verify(valueOperations).set(
                eq("tracking:event:" + envelope.eventId()), eq("PROCESSED"), eq(Duration.ofHours(24)));
    }
}

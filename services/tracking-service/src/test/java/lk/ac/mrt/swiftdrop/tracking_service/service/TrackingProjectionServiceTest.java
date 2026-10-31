package lk.ac.mrt.swiftdrop.tracking_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1.SnapshotTimelineEntryV1;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.repository.RedisTrackingRepository;
import lk.ac.mrt.swiftdrop.tracking_service.service.impl.DefaultTrackingProjectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrackingProjectionServiceTest {

    @Mock
    private RedisTrackingRepository redisRepository;

    @Mock
    private ParcelServiceClient parcelClient;

    @Mock
    private lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingRealtimePublisher realtimePublisher;

    private DefaultTrackingProjectionService projectionService;

    private final String tn = "SD-4F7K2Q9M";

    @BeforeEach
    void setUp() {
        projectionService = new DefaultTrackingProjectionService(redisRepository, parcelClient, realtimePublisher);
    }

    @Test
    void timelineOrderIsStableAcrossOutOfOrderEvents() {
        Instant t1 = Instant.parse("2026-10-10T09:00:00Z");
        Instant t2 = Instant.parse("2026-10-10T11:00:00Z");

        // Existing state in Redis is IN_TRANSIT at t2
        TrackingProjection existing = new TrackingProjection(
                tn,
                ParcelStatus.IN_TRANSIT,
                "Colombo",
                "Kandy",
                "Kasun",
                null,
                t2,
                List.of(new TimelineEntry(ParcelStatus.IN_TRANSIT, t2, null))
        );
        when(redisRepository.findByTrackingNumber(tn)).thenReturn(Optional.of(existing));

        // Arrives an out-of-order older event: PICKED_UP at t1 (t1 < t2)
        EventEnvelope<ParcelStatusChangedV1> olderEvent = new EventEnvelope<>(
                UUID.randomUUID(),
                ParcelStatusChangedV1.EVENT_TYPE,
                ParcelStatusChangedV1.VERSION,
                t1,
                "parcel-service",
                new ParcelStatusChangedV1(UUID.randomUUID(), tn, ParcelStatus.CREATED, ParcelStatus.PICKED_UP, null, null)
        );

        projectionService.applyStatusChanged(olderEvent);

        ArgumentCaptor<TrackingProjection> captor = ArgumentCaptor.forClass(TrackingProjection.class);
        verify(redisRepository).saveProjection(captor.capture());

        TrackingProjection saved = captor.getValue();
        // Status did NOT regress
        assertThat(saved.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(saved.updatedAt()).isEqualTo(t2);
        // Timeline has both entries in ascending order
        assertThat(saved.timeline()).hasSize(2);
        assertThat(saved.timeline().get(0).status()).isEqualTo(ParcelStatus.PICKED_UP);
        assertThat(saved.timeline().get(0).occurredAt()).isEqualTo(t1);
        assertThat(saved.timeline().get(1).status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(saved.timeline().get(1).occurredAt()).isEqualTo(t2);
    }

    @Test
    void appliesV2StatusEventFromCachedProjectionWithoutParcelServiceCall() {
        Instant pickedUpAt = Instant.parse("2026-10-10T09:00:00Z");
        Instant inTransitAt = Instant.parse("2026-10-10T10:00:00Z");
        TrackingProjection existing = new TrackingProjection(
                tn,
                ParcelStatus.PICKED_UP,
                "Colombo",
                "Kandy",
                "Courier",
                null,
                pickedUpAt,
                List.of(new TimelineEntry(ParcelStatus.PICKED_UP, pickedUpAt, null))
        );
        when(redisRepository.findByTrackingNumber(tn)).thenReturn(Optional.of(existing));

        var payload = new ParcelStatusChangedV2(
                UUID.randomUUID(),
                tn,
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                ParcelStatus.PICKED_UP,
                ParcelStatus.IN_TRANSIT,
                UUID.randomUUID(),
                null,
                null);
        var envelope = new EventEnvelope<>(
                UUID.randomUUID(),
                ParcelStatusChangedV2.EVENT_TYPE,
                ParcelStatusChangedV2.VERSION,
                inTransitAt,
                "parcel-service",
                payload);

        projectionService.applyStatusChangedV2(envelope);

        ArgumentCaptor<TrackingProjection> captor = ArgumentCaptor.forClass(TrackingProjection.class);
        verify(redisRepository).saveProjection(captor.capture());
        TrackingProjection saved = captor.getValue();
        assertThat(saved.status()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(saved.updatedAt()).isEqualTo(inTransitAt);
        assertThat(saved.originCity()).isEqualTo("Colombo");
        assertThat(saved.destinationCity()).isEqualTo("Kandy");
        assertThat(saved.courierFirstName()).isEqualTo("Courier");
        assertThat(saved.timeline()).extracting(TimelineEntry::status)
                .containsExactly(ParcelStatus.PICKED_UP, ParcelStatus.IN_TRANSIT);
        verify(parcelClient, org.mockito.Mockito.never()).fetchSnapshot(tn);
    }

    @Test
    void coldCacheBackfillsFromParcelServiceOnce() {
        when(redisRepository.findByTrackingNumber(tn)).thenReturn(Optional.empty());

        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        ParcelTrackingSnapshotV1 snapshot = new ParcelTrackingSnapshotV1(
                UUID.randomUUID(),
                tn,
                ParcelStatus.PICKED_UP,
                "Colombo",
                "Galle",
                "Nimal",
                "2026-10-11T10:00:00Z",
                now,
                List.of(new SnapshotTimelineEntryV1(ParcelStatus.PICKED_UP, now, "Picked up"))
        );
        when(parcelClient.fetchSnapshot(tn)).thenReturn(Optional.of(snapshot));

        Optional<TrackingProjection> result = projectionService.getProjection(tn);

        assertThat(result).isPresent();
        assertThat(result.get().trackingNumber()).isEqualTo(tn);
        assertThat(result.get().originCity()).isEqualTo("Colombo");
        assertThat(result.get().destinationCity()).isEqualTo("Galle");
        assertThat(result.get().courierFirstName()).isEqualTo("Nimal");
        assertThat(result.get().status()).isEqualTo(ParcelStatus.PICKED_UP);

        // Verify it was saved to Redis
        verify(redisRepository, times(1)).saveProjection(any());
    }

    @Test
    void degradesToDirectParcelReadDuringRedisOutage() {
        // Redis throws exception indicating outage
        when(redisRepository.findByTrackingNumber(tn))
                .thenThrow(new RedisTrackingRepository.RedisStorageException("Connection refused", new RuntimeException()));

        Instant now = Instant.parse("2026-10-10T12:00:00Z");
        ParcelTrackingSnapshotV1 snapshot = new ParcelTrackingSnapshotV1(
                UUID.randomUUID(),
                tn,
                ParcelStatus.DELIVERED,
                "Colombo",
                "Jaffna",
                "Sunil",
                null,
                now,
                List.of(new SnapshotTimelineEntryV1(ParcelStatus.DELIVERED, now, "Delivered"))
        );
        when(parcelClient.fetchSnapshot(tn)).thenReturn(Optional.of(snapshot));

        // Must degrade gracefully and not throw exception
        Optional<TrackingProjection> result = projectionService.getProjection(tn);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(ParcelStatus.DELIVERED);
        assertThat(result.get().destinationCity()).isEqualTo("Jaffna");
    }
}

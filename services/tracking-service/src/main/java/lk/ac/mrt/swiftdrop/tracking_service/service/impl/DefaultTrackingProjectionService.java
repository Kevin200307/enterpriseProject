package lk.ac.mrt.swiftdrop.tracking_service.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.repository.RedisTrackingRepository;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultTrackingProjectionService implements TrackingProjectionService {

    private final RedisTrackingRepository redisRepository;
    private final ParcelServiceClient parcelClient;

    @Override
    public void applyStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        String tn = envelope.payload().trackingNumber();
        ParcelStatus newStatus = envelope.payload().toStatus();
        Instant occurredAt = envelope.occurredAt();

        // Load existing or backfill
        Optional<TrackingProjection> existingOpt = Optional.empty();
        try {
            existingOpt = redisRepository.findByTrackingNumber(tn);
        } catch (Exception ex) {
            log.warn("Redis read failed during applyStatusChanged for {}: {}", tn, ex.getMessage());
        }

        TrackingProjection base = existingOpt.orElseGet(() ->
                parcelClient.fetchSnapshot(tn)
                        .map(this::fromSnapshot)
                        .orElseGet(() -> new TrackingProjection(
                                tn,
                                newStatus,
                                "Unknown",
                                "Unknown",
                                null,
                                null,
                                occurredAt,
                                List.of()
                        ))
        );

        // Out-of-order protection: older event does not regress current status
        boolean isNewer = occurredAt.isAfter(base.updatedAt())
                || (occurredAt.equals(base.updatedAt()) && envelope.eventId().compareTo(UUID_ZERO) > 0);

        ParcelStatus resolvedStatus = isNewer ? newStatus : base.status();
        Instant resolvedUpdatedAt = isNewer ? occurredAt : base.updatedAt();

        // Update timeline
        List<TimelineEntry> updatedTimeline = new ArrayList<>(base.timeline());
        boolean duplicate = updatedTimeline.stream()
                .anyMatch(t -> t.status() == newStatus && t.occurredAt().equals(occurredAt));
        if (!duplicate) {
            updatedTimeline.add(new TimelineEntry(newStatus, occurredAt, null));
            updatedTimeline.sort(Comparator.comparing(TimelineEntry::occurredAt));
        }

        TrackingProjection updated = new TrackingProjection(
                tn,
                resolvedStatus,
                base.originCity(),
                base.destinationCity(),
                base.courierFirstName(),
                base.eta(),
                resolvedUpdatedAt,
                updatedTimeline
        );

        try {
            redisRepository.saveProjection(updated);
        } catch (Exception ex) {
            log.error("Failed to save projection for {} into Redis: {}", tn, ex.getMessage());
            throw ex;
        }
    }

    @Override
    public void applySnapshot(EventEnvelope<ParcelTrackingSnapshotV1> envelope) {
        TrackingProjection projection = fromSnapshot(envelope.payload());
        try {
            redisRepository.saveProjection(projection);
        } catch (Exception ex) {
            log.error("Failed to save snapshot projection for {} to Redis: {}", projection.trackingNumber(), ex.getMessage());
            throw ex;
        }
    }

    @Override
    public Optional<TrackingProjection> getProjection(String trackingNumber) {
        // Step 1: try Redis
        try {
            Optional<TrackingProjection> cached = redisRepository.findByTrackingNumber(trackingNumber);
            if (cached.isPresent()) {
                return cached;
            }
        } catch (Exception ex) {
            log.warn("Redis outage detected during getProjection for {}; degrading to direct parcel-service read: {}",
                    trackingNumber, ex.getMessage());
            return parcelClient.fetchSnapshot(trackingNumber).map(this::fromSnapshot);
        }

        // Step 2: cold cache miss -> backfill from parcel-service
        log.info("Cold cache miss for tracking number {}; backfilling from parcel-service", trackingNumber);
        Optional<ParcelTrackingSnapshotV1> snapshotOpt = parcelClient.fetchSnapshot(trackingNumber);
        if (snapshotOpt.isEmpty()) {
            return Optional.empty();
        }

        TrackingProjection projection = fromSnapshot(snapshotOpt.get());
        try {
            redisRepository.saveProjection(projection);
        } catch (Exception ex) {
            log.warn("Failed to cache backfilled projection for {} into Redis: {}", trackingNumber, ex.getMessage());
        }

        return Optional.of(projection);
    }

    private TrackingProjection fromSnapshot(ParcelTrackingSnapshotV1 s) {
        List<TimelineEntry> timeline = s.timeline().stream()
                .map(t -> new TimelineEntry(t.status(), t.occurredAt(), t.note()))
                .sorted(Comparator.comparing(TimelineEntry::occurredAt))
                .toList();

        return new TrackingProjection(
                s.trackingNumber(),
                s.status(),
                s.originCity(),
                s.destinationCity(),
                s.courierFirstName(),
                s.eta(),
                s.updatedAt(),
                timeline
        );
    }

    private static final java.util.UUID UUID_ZERO = new java.util.UUID(0L, 0L);
}

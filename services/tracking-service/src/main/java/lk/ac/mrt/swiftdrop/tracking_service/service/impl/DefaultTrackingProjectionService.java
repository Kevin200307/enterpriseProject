package lk.ac.mrt.swiftdrop.tracking_service.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.metrics.TrackingMetrics;
import lk.ac.mrt.swiftdrop.tracking_service.realtime.TrackingRealtimePublisher;
import lk.ac.mrt.swiftdrop.tracking_service.repository.RedisTrackingRepository;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultTrackingProjectionService implements TrackingProjectionService {

    private final RedisTrackingRepository redisRepository;
    private final ParcelServiceClient parcelClient;
    private final TrackingRealtimePublisher realtimePublisher;
    private final TrackingMetrics metrics;

    @Autowired
    public DefaultTrackingProjectionService(
            RedisTrackingRepository redisRepository,
            ParcelServiceClient parcelClient,
            TrackingRealtimePublisher realtimePublisher,
            @Autowired(required = false) TrackingMetrics metrics) {
        this.redisRepository = redisRepository;
        this.parcelClient = parcelClient;
        this.realtimePublisher = realtimePublisher;
        this.metrics = metrics;
    }

    public DefaultTrackingProjectionService(
            RedisTrackingRepository redisRepository,
            ParcelServiceClient parcelClient,
            TrackingRealtimePublisher realtimePublisher) {
        this(redisRepository, parcelClient, realtimePublisher, null);
    }

    @Override
    public void applyStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        applyStatusChanged(envelope.eventId(), envelope.payload().trackingNumber(),
                envelope.payload().toStatus(), envelope.occurredAt(), false);
    }

    @Override
    public void applyStatusChangedV2(EventEnvelope<ParcelStatusChangedV2> envelope) {
        applyStatusChanged(envelope.eventId(), envelope.payload().trackingNumber(),
                envelope.payload().toStatus(), envelope.occurredAt(), true);
    }

    private void applyStatusChanged(UUID eventId, String tn, ParcelStatus newStatus,
                                    Instant occurredAt, boolean requireBackfill) {
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
                        .orElse(null)
        );

        if (base == null) {
            if (requireBackfill) {
                throw new IllegalStateException("Unable to find projection or backfill snapshot for " + tn);
            }
            log.warn("Unable to find projection or backfill snapshot for {}; dropping status update", tn);
            return;
        }

        // Tie-breaking: older events cannot regress current status
        boolean shouldAdvanceStatus = occurredAt.isAfter(base.updatedAt())
                || (occurredAt.equals(base.updatedAt()) && eventId.compareTo(UUID_ZERO) >= 0);

        if (!shouldAdvanceStatus && metrics != null) {
            metrics.incrementOutOfOrder();
        }

        ParcelStatus resolvedStatus = shouldAdvanceStatus ? newStatus : base.status();
        Instant resolvedUpdatedAt = shouldAdvanceStatus ? occurredAt : base.updatedAt();

        List<TimelineEntry> updatedTimeline = new ArrayList<>(base.timeline());
        boolean alreadyInTimeline = updatedTimeline.stream()
                .anyMatch(t -> t.status() == newStatus && t.occurredAt().equals(occurredAt));

        if (!alreadyInTimeline) {
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
            if (realtimePublisher != null) {
                realtimePublisher.publishUpdate(updated.toPublicDto());
            }
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
            if (realtimePublisher != null) {
                realtimePublisher.publishUpdate(projection.toPublicDto());
            }
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
            if (metrics != null) {
                metrics.incrementRedisFallback();
            }
            return parcelClient.fetchSnapshot(trackingNumber).map(this::fromSnapshot);
        }

        // Step 2: cold miss backfill
        log.info("Cold cache miss for {}; backfilling from parcel-service", trackingNumber);
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

    private static final java.util.UUID UUID_ZERO = new java.util.UUID(0, 0);
}

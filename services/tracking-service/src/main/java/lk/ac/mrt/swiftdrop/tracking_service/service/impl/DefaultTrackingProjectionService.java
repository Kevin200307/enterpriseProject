package lk.ac.mrt.swiftdrop.tracking_service.service.impl;

import java.util.Optional;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DefaultTrackingProjectionService implements TrackingProjectionService {

    @Override
    public void applyStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope) {
        log.debug("applyStatusChanged: {}", envelope.eventId());
    }

    @Override
    public void applySnapshot(EventEnvelope<ParcelTrackingSnapshotV1> envelope) {
        log.debug("applySnapshot: {}", envelope.eventId());
    }

    @Override
    public Optional<TrackingProjection> getProjection(String trackingNumber) {
        return Optional.empty();
    }
}

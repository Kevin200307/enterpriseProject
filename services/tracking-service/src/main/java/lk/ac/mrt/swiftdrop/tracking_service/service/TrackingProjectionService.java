package lk.ac.mrt.swiftdrop.tracking_service.service;

import java.util.Optional;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelTrackingSnapshotV1;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;

public interface TrackingProjectionService {

    void applyStatusChanged(EventEnvelope<ParcelStatusChangedV1> envelope);

    void applySnapshot(EventEnvelope<ParcelTrackingSnapshotV1> envelope);

    Optional<TrackingProjection> getProjection(String trackingNumber);
}

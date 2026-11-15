package lk.ac.mrt.swiftdrop.tracking_service.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.tracking_service.audit.TrackingAuditLogger;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelDetails;
import lk.ac.mrt.swiftdrop.tracking_service.client.ParcelServiceClient;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PrivateTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrivateTrackingService {

    private final ParcelServiceClient parcelServiceClient;
    private final TrackingProjectionService projectionService;
    private final TrackingAuditLogger auditLogger;

    public PrivateTracking getPrivateTracking(String trackingNumber, UUID userId, Set<String> roles, UUID merchantId) {
        if (!TrackingNumber.isValid(trackingNumber)) {
            throw new TrackingException(ErrorCode.VALIDATION_FAILED, "Invalid tracking number format: " + trackingNumber);
        }

        ParcelDetails parcel = parcelServiceClient.fetchParcelDetails(trackingNumber)
                .orElseThrow(() -> new TrackingException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found"));

        // Validate role-based ownership:
        // Return 404 rather than 403 when parcel is not owned/visible to caller to prevent enumeration.
        boolean authorized = false;

        if (roles.contains("ADMIN") || roles.contains("DISPATCHER")) {
            authorized = true;
        } else if (roles.contains("CUSTOMER") && parcel.customerId() != null && parcel.customerId().equals(userId)) {
            authorized = true;
        } else if (roles.contains("MERCHANT") && merchantId != null && parcel.merchantId() != null && parcel.merchantId().equals(merchantId)) {
            authorized = true;
        } else if (roles.contains("COURIER") && parcel.assigneeId() != null && parcel.assigneeId().equals(userId)) {
            authorized = true;
        }

        if (!authorized) {
            throw new TrackingException(ErrorCode.RESOURCE_NOT_FOUND, "Parcel was not found");
        }

        // Audit log hook for PII access
        auditLogger.logPiiAccess(trackingNumber, userId, roles, "GET_FULL_TRACKING");

        List<TimelineEntry> timeline = projectionService.getProjection(trackingNumber)
                .map(TrackingProjection::timeline)
                .orElse(List.of());

        PrivateTracking.PrivateAddress pickup = parcel.pickup() != null ? new PrivateTracking.PrivateAddress(
                parcel.pickup().contactName(),
                parcel.pickup().phone(),
                parcel.pickup().line1(),
                parcel.pickup().city(),
                parcel.pickup().postalCode(),
                parcel.pickup().zone()
        ) : null;

        PrivateTracking.PrivateAddress dropoff = parcel.dropoff() != null ? new PrivateTracking.PrivateAddress(
                parcel.dropoff().contactName(),
                parcel.dropoff().phone(),
                parcel.dropoff().line1(),
                parcel.dropoff().city(),
                parcel.dropoff().postalCode(),
                parcel.dropoff().zone()
        ) : null;

        return new PrivateTracking(
                parcel.id(),
                parcel.trackingNumber(),
                parcel.status(),
                parcel.customerId(),
                parcel.merchantId(),
                parcel.assigneeId(),
                pickup,
                dropoff,
                parcel.size(),
                parcel.weightKg(),
                parcel.fee(),
                parcel.currency(),
                parcel.createdAt(),
                parcel.updatedAt(),
                timeline
        );
    }
}

package lk.ac.mrt.swiftdrop.tracking_service.api;

import java.util.concurrent.TimeUnit;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingException;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public and unauthenticated tracking endpoints.
 * Never exposes customer PII or internal IDs.
 */
@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingProjectionService projectionService;

    @GetMapping("/{trackingNumber}")
    public ResponseEntity<PublicTracking> getPublicTracking(
            @PathVariable String trackingNumber,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (!TrackingNumber.isValid(trackingNumber)) {
            throw new TrackingException(ErrorCode.VALIDATION_FAILED, "Malformed tracking number: " + trackingNumber);
        }

        TrackingProjection projection = projectionService.getProjection(trackingNumber)
                .orElseThrow(() -> new TrackingException(ErrorCode.RESOURCE_NOT_FOUND, "Tracking number was not found"));

        PublicTracking response = projection.toPublicDto();

        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(10, TimeUnit.SECONDS).cachePublic());

        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }

        return builder.body(response);
    }
}

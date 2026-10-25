package lk.ac.mrt.swiftdrop.tracking_service.api;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lk.ac.mrt.swiftdrop.contracts.domain.TrackingNumber;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PrivateTracking;
import lk.ac.mrt.swiftdrop.tracking_service.dto.PublicTracking;
import lk.ac.mrt.swiftdrop.tracking_service.error.TrackingException;
import lk.ac.mrt.swiftdrop.tracking_service.service.PrivateTrackingService;
import lk.ac.mrt.swiftdrop.tracking_service.service.TrackingProjectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for tracking parcels:
 * - Public tracking: Privacy-safe unauthenticated view.
 * - Private tracking: Authenticated full view with role/ownership validation.
 */
@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final TrackingProjectionService projectionService;
    private final PrivateTrackingService privateTrackingService;

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

    @GetMapping("/{trackingNumber}/full")
    public ResponseEntity<PrivateTracking> getPrivateTracking(
            @PathVariable String trackingNumber,
            Authentication authentication,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {

        if (authentication == null) {
            throw new TrackingException(ErrorCode.UNAUTHENTICATED, "A valid bearer token is required");
        }

        UUID userId;
        try {
            userId = UUID.fromString(authentication.getName());
        } catch (Exception ex) {
            throw new TrackingException(ErrorCode.UNAUTHENTICATED, "A valid UUID subject is required");
        }

        Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(r -> r.startsWith("ROLE_") ? r.substring(5) : r)
                .collect(Collectors.toCollection(HashSet::new));

        UUID merchantId = null;
        if (authentication instanceof JwtAuthenticationToken jwtToken) {
            Jwt jwt = jwtToken.getToken();
            if (jwt.hasClaim("merchant_id")) {
                try {
                    merchantId = UUID.fromString(jwt.getClaimAsString("merchant_id"));
                } catch (Exception ignored) {
                }
            }
            if (jwt.hasClaim("roles")) {
                List<String> claimRoles = jwt.getClaimAsStringList("roles");
                if (claimRoles != null) {
                    roles.addAll(claimRoles);
                }
            }
        }

        PrivateTracking response = privateTrackingService.getPrivateTracking(trackingNumber, userId, roles, merchantId);

        var builder = ResponseEntity.ok()
                .cacheControl(CacheControl.noStore());

        if (requestId != null && !requestId.isBlank()) {
            builder.header("X-Request-Id", requestId);
        }

        return builder.body(response);
    }
}

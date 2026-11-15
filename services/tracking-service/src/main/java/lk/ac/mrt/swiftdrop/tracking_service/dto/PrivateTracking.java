package lk.ac.mrt.swiftdrop.tracking_service.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

/**
 * Full private view containing sensitive customer/courier PII, pricing, and exact delivery addresses.
 * Strictly access-controlled and separated from PublicTracking.
 */
public record PrivateTracking(
        UUID parcelId,
        String trackingNumber,
        ParcelStatus status,
        UUID customerId,
        UUID merchantId,
        UUID courierId,
        PrivateAddress pickup,
        PrivateAddress dropoff,
        PackageSize size,
        BigDecimal weightKg,
        BigDecimal fee,
        String currency,
        Instant createdAt,
        Instant updatedAt,
        List<TimelineEntry> timeline
) {
    public record PrivateAddress(
            String contactName,
            String phone,
            String line1,
            String city,
            String postalCode,
            String zone
    ) {}
}

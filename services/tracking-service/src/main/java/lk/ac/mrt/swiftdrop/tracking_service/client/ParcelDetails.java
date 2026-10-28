package lk.ac.mrt.swiftdrop.tracking_service.client;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

public record ParcelDetails(
        UUID id,
        String trackingNumber,
        UUID customerId,
        UUID merchantId,
        UUID assigneeId,
        AddressView pickup,
        AddressView dropoff,
        PackageSize size,
        BigDecimal weightKg,
        BigDecimal fee,
        String currency,
        ParcelStatus status,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public record AddressView(
            String contactName,
            String phone,
            String line1,
            String city,
            String postalCode,
            String zone
    ) {}
}

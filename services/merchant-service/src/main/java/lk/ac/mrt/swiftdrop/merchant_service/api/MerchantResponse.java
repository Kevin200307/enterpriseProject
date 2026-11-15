package lk.ac.mrt.swiftdrop.merchant_service.api;

import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;

import java.time.Instant;
import java.util.UUID;

public record MerchantResponse(
        UUID id,
        UUID ownerUserId,
        String orgName,
        String businessRegNo,
        String contactEmail,
        String contactPhone,
        MerchantStatus status,
        UUID approvedBy,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

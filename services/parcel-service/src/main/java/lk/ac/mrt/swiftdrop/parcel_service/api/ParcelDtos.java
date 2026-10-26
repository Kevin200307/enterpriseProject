package lk.ac.mrt.swiftdrop.parcel_service.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.*;
import org.springframework.data.domain.Page;

public final class ParcelDtos {
    private ParcelDtos() {}

    public record AddressInput(
        @NotBlank @Size(max=120) String contactName,
        @NotBlank @Pattern(regexp="[+0-9() -]{7,25}") String phone,
        @NotBlank @Size(max=255) String line1,
        @NotBlank @Size(max=100) String city,
        @NotBlank @Size(max=20) String postalCode,
        @NotBlank @Size(max=80) String zone) {
        public Address toAddress() { return new Address(contactName, phone, line1, city, postalCode, zone); }
    }

    public record BookingRequest(
        @NotNull @Valid AddressInput pickup,
        @NotNull @Valid AddressInput dropoff,
        @NotNull PackageSize size,
        @NotNull @DecimalMin("0.001") @DecimalMax("1000.000")
        @Digits(integer=4, fraction=3) BigDecimal weightKg) {}

    public record StatusRequest(@NotNull ParcelStatus status, @Size(max=1000) String note,
                                UUID proofId, @Size(max=500) String failedReason) {}
    public record AssignmentRequest(@NotNull UUID courierId) {}

    public record ParcelView(UUID id, String trackingNumber, UUID customerId, UUID merchantId, UUID assigneeId,
        Address pickup, Address dropoff, PackageSize size, BigDecimal weightKg, BigDecimal fee,
        String currency, ParcelStatus status, Instant createdAt, Instant updatedAt, long version) {
        public static ParcelView of(Parcel p) {
            return new ParcelView(p.getId(), p.getTrackingNumber(), p.getCustomerId(), p.getMerchantId(), p.getAssigneeId(),
                p.getPickup(), p.getDropoff(), p.getSize(), p.getWeightKg(), p.getFee(), p.getCurrency(),
                p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), p.getVersion());
        }
    }

    public record HistoryView(UUID id, ParcelStatus fromStatus, ParcelStatus toStatus, UUID actorId,
                              String note, UUID proofId, String failedReason, Instant occurredAt) {
        public static HistoryView of(StatusHistory h) {
            return new HistoryView(h.getId(), h.getFromStatus(), h.getToStatus(), h.getActorId(),
                h.getNote(), h.getProofId(), h.getFailedReason(), h.getOccurredAt());
        }
    }
    public record ProofUrl(String url, Instant expiresAt) {}
    public record ProofDownload(String contentType, byte[] content) {}
    public record ProofView(UUID id, String contentType, long sizeBytes, Instant createdAt) {}
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> of(Page<T> p) {
            return new PageView<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}

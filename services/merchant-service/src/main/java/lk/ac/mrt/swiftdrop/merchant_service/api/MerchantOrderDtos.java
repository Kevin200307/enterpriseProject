package lk.ac.mrt.swiftdrop.merchant_service.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;

public final class MerchantOrderDtos {
    private MerchantOrderDtos() {}
    public record Address(@NotBlank String contactName, @NotBlank String phone, @NotBlank String line1,
                          @NotBlank String city, @NotBlank String postalCode, @NotBlank String zone) {}
    public record Create(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{3,100}") String merchantRef,
                         @NotNull @Valid Address pickup, @NotNull @Valid Address dropoff,
                         @NotNull PackageSize size, @NotNull @DecimalMin("0.001") BigDecimal weightKg) {}
    public record View(UUID id, String merchantRef, UUID parcelId, String trackingNumber, String status,
                       String destinationCity, BigDecimal fee, String currency, Instant createdAt) {}
}

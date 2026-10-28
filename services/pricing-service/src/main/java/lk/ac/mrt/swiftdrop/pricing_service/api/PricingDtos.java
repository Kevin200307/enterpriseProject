package lk.ac.mrt.swiftdrop.pricing_service.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import org.springframework.util.StringUtils;

public final class PricingDtos {
    private PricingDtos() {}

    public record ZoneInput(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "[A-Z0-9_-]{2,12}", message = "must be an uppercase zone code") String code,
        @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 120) String> cities,
        boolean active
    ) {
        @AssertTrue(message = "city names must be unique ignoring case and whitespace")
        public boolean hasUniqueNormalizedCities() {
            if (cities == null) return true;
            return cities.stream().filter(StringUtils::hasText)
                .map(city -> city.trim().toLowerCase(java.util.Locale.ROOT)).distinct().count()
                == cities.stream().filter(StringUtils::hasText).count();
        }
    }

    public record ZoneView(UUID id, String name, String code, List<String> cities, boolean active) {
        public static ZoneView of(Zone zone) {
            return new ZoneView(zone.getId(), zone.getName(), zone.getCode(),
                zone.getCities().stream().map(c -> c.getCityName()).sorted(String.CASE_INSENSITIVE_ORDER).toList(), zone.isActive());
        }
    }

    public record PricingRuleInput(
        @NotNull UUID zoneFromId,
        @NotNull UUID zoneToId,
        @NotNull PackageSize sizeBand,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal baseFee,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal perKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal minFee,
        @NotNull @DecimalMin(value = "0.001", inclusive = true) @Digits(integer = 7, fraction = 3) BigDecimal maxWeightKg,
        boolean active,
        @NotNull Instant effectiveFrom,
        Instant effectiveTo,
        Long version
    ) {
        @AssertTrue(message = "effectiveTo must be after effectiveFrom")
        public boolean hasValidEffectiveRange() {
            return effectiveTo == null || effectiveFrom == null || effectiveFrom.isBefore(effectiveTo);
        }
    }

    public record PricingRuleView(UUID id, UUID zoneFromId, UUID zoneToId, PackageSize sizeBand,
        BigDecimal baseFee, BigDecimal perKg, BigDecimal minFee, BigDecimal maxWeightKg,
        boolean active, Instant effectiveFrom, Instant effectiveTo, long version) {
        public static PricingRuleView of(PricingRule rule) {
            return new PricingRuleView(rule.getId(), rule.getZoneFrom().getId(), rule.getZoneTo().getId(),
                rule.getSizeBand(), rule.getBaseFee(), rule.getPerKg(), rule.getMinFee(), rule.getMaxWeightKg(),
                rule.isActive(), rule.getEffectiveFrom(), rule.getEffectiveTo(), rule.getVersion());
        }
    }

    public record EstimateRequest(
        @NotBlank @Size(max = 120) String fromCity,
        @NotBlank @Size(max = 120) String toCity,
        @NotNull PackageSize size,
        @NotNull @DecimalMin(value = "0.001") @Digits(integer = 7, fraction = 3) BigDecimal weightKg,
        boolean express
    ) {}

    public record EstimateResponse(String zoneFrom, String zoneTo, BigDecimal baseFee,
        BigDecimal weightSurcharge, BigDecimal expressSurcharge, BigDecimal total,
        String currency, UUID ruleId, Instant calculatedAt) {}
}
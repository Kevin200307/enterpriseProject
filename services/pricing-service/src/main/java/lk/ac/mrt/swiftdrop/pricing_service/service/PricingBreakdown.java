package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;

public record PricingBreakdown(BigDecimal baseFee, BigDecimal weightSurcharge, BigDecimal expressSurcharge) {
    public static PricingBreakdown empty() { return new PricingBreakdown(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO); }
    public PricingBreakdown withBaseFee(BigDecimal amount) { return new PricingBreakdown(amount, weightSurcharge, expressSurcharge); }
    public PricingBreakdown withWeightSurcharge(BigDecimal amount) { return new PricingBreakdown(baseFee, amount, expressSurcharge); }
    public PricingBreakdown withExpressSurcharge(BigDecimal amount) { return new PricingBreakdown(baseFee, weightSurcharge, amount); }
}
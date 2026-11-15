package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class WeightSurchargeStrategy implements PricingStrategy {
    @Override public int order() { return 20; }
    @Override public PricingBreakdown apply(PricingContext context, PricingBreakdown current) {
        BigDecimal excess = context.weightKg().subtract(context.rule().getIncludedWeightKg()).max(BigDecimal.ZERO);
        BigDecimal surcharge = context.rule().getPerKg().multiply(excess).setScale(2, RoundingMode.HALF_UP);
        return current.withWeightSurcharge(surcharge);
    }
}
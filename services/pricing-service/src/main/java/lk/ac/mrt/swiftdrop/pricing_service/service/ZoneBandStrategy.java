package lk.ac.mrt.swiftdrop.pricing_service.service;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class ZoneBandStrategy implements PricingStrategy {
    @Override public int order() { return 10; }
    @Override public PricingBreakdown apply(PricingContext context, PricingBreakdown current) {
        return current.withBaseFee(context.rule().getBaseFee());
    }
}
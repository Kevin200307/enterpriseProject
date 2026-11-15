package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class ExpressSurchargeStrategy implements PricingStrategy {
    @Override public int order() { return 30; }
    @Override public PricingBreakdown apply(PricingContext context, PricingBreakdown current) {
        if (!context.express()) return current.withExpressSurcharge(BigDecimal.ZERO.setScale(2));
        BigDecimal regular = current.baseFee().max(context.rule().getMinFee()).add(current.weightSurcharge());
        BigDecimal percent = context.rule().getExpressSurchargePercent().movePointLeft(2);
        return current.withExpressSurcharge(regular.multiply(percent).setScale(2, RoundingMode.HALF_UP));
    }
}
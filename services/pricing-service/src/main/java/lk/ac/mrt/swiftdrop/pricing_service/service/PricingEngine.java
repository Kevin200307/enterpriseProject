package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PricingEngine {
    private final List<PricingStrategy> strategies;

    public PricingEngine(List<PricingStrategy> strategies) {
        this.strategies = strategies.stream().sorted(Comparator.comparingInt(PricingStrategy::order)).toList();
    }

    public Result calculate(PricingContext context) {
        PricingBreakdown breakdown = strategies.stream().reduce(PricingBreakdown.empty(),
            (current, strategy) -> strategy.apply(context, current), (left, right) -> right);
        BigDecimal base = money(breakdown.baseFee().max(context.rule().getMinFee()));
        BigDecimal weight = money(breakdown.weightSurcharge());
        BigDecimal express = money(breakdown.expressSurcharge());
        BigDecimal total = money(base.add(weight).add(express));
        return new Result(base, weight, express, total);
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    public record Result(BigDecimal baseFee, BigDecimal weightSurcharge, BigDecimal expressSurcharge, BigDecimal total) {}
}
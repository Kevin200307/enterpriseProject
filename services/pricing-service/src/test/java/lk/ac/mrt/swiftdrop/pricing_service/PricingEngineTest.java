package lk.ac.mrt.swiftdrop.pricing_service;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import lk.ac.mrt.swiftdrop.pricing_service.service.*;
import org.junit.jupiter.api.Test;

class PricingEngineTest {
    private static BigDecimal d(String value) { return new BigDecimal(value); }

    private PricingRule rule() {
        Zone zone = new Zone("Colombo", "CMB", java.util.Set.of(), true);
        return new PricingRule(zone, zone, PackageSize.SMALL, d("100.00"), d("50.00"), d("200.00"),
            d("10.000"), d("1.000"), d("10.00"), true, Instant.parse("2020-01-01T00:00:00Z"), null);
    }

    @Test
    void strategiesProduceRoundedLkrBreakdownAndTotal() {
        var engine = new PricingEngine(List.of(new ExpressSurchargeStrategy(), new WeightSurchargeStrategy(), new ZoneBandStrategy()));
        var result = engine.calculate(new PricingContext(rule(), d("2.000"), true, Instant.now()));
        assertThat(result.baseFee()).isEqualByComparingTo("200.00");
        assertThat(result.weightSurcharge()).isEqualByComparingTo("50.00");
        assertThat(result.expressSurcharge()).isEqualByComparingTo("25.00");
        assertThat(result.total()).isEqualByComparingTo("275.00");
    }

    @Test
    void includedWeightHasNoSurchargeAndExpressCanBeDisabled() {
        var engine = new PricingEngine(List.of(new ZoneBandStrategy(), new WeightSurchargeStrategy(), new ExpressSurchargeStrategy()));
        var result = engine.calculate(new PricingContext(rule(), d("1.000"), false, Instant.now()));
        assertThat(result.weightSurcharge()).isEqualByComparingTo("0.00");
        assertThat(result.expressSurcharge()).isEqualByComparingTo("0.00");
        assertThat(result.total()).isEqualByComparingTo("200.00");
    }

    @Test
    void customStrategyCanBeAddedWithoutEngineChanges() {
        PricingStrategy insurance = new PricingStrategy() {
            @Override public int order() { return 40; }
            @Override public PricingBreakdown apply(PricingContext context, PricingBreakdown current) {
                return current.withExpressSurcharge(current.expressSurcharge().add(d("2.345")));
            }
        };
        var engine = new PricingEngine(List.of(insurance, new ExpressSurchargeStrategy(), new WeightSurchargeStrategy(), new ZoneBandStrategy()));
        var result = engine.calculate(new PricingContext(rule(), d("1.000"), false, Instant.now()));
        assertThat(result.expressSurcharge()).isEqualByComparingTo("2.35");
        assertThat(result.total()).isEqualByComparingTo("202.35");
    }
}
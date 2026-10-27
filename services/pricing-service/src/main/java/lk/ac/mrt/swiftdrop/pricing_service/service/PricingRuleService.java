package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.PricingRuleInput;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.PricingRuleView;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import lk.ac.mrt.swiftdrop.pricing_service.repository.PricingRuleRepository;
import lk.ac.mrt.swiftdrop.pricing_service.repository.ZoneRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PricingRuleService {
    private final PricingRuleRepository rules;
    private final ZoneRepository zones;
    private final BigDecimal includedWeightKg;
    private final BigDecimal expressPercent;
    public PricingRuleService(PricingRuleRepository rules, ZoneRepository zones,
            @Value("${pricing.calculation.included-weight-kg:1.00}") BigDecimal includedWeightKg,
            @Value("${pricing.calculation.express-surcharge-percent:15.00}") BigDecimal expressPercent) {
        this.rules = rules; this.zones = zones; this.includedWeightKg = includedWeightKg; this.expressPercent = expressPercent;
    }

    @Transactional(readOnly = true)
    public List<PricingRuleView> list(boolean includeInactive) {
        return (includeInactive ? rules.findAllByOrderByEffectiveFromDesc() : rules.findByActiveTrueOrderByEffectiveFromDesc())
            .stream().map(PricingRuleView::of).toList();
    }

    @CacheEvict(cacheNames = "pricingEstimates", allEntries = true)
    public PricingRuleView create(PricingRuleInput input) {
        Zone from = zone(input.zoneFromId()); Zone to = zone(input.zoneToId());
        validateRule(input, from, to, null);
        var rule = new PricingRule(from, to, input.sizeBand(), money(input.baseFee()), money(input.perKg()),
            money(input.minFee()), input.maxWeightKg(), includedWeightKg, expressPercent, input.active(),
            input.effectiveFrom(), input.effectiveTo());
        return PricingRuleView.of(rules.save(rule));
    }

    @CacheEvict(cacheNames = "pricingEstimates", allEntries = true)
    public PricingRuleView update(UUID id, PricingRuleInput input) {
        PricingRule rule = rules.findById(id).orElseThrow(() -> new PricingException(ErrorCode.RESOURCE_NOT_FOUND, "Pricing rule was not found"));
        if (input.version() != null && input.version() != rule.getVersion()) throw new PricingException(ErrorCode.CONFLICT,
            "Pricing rule has changed; refresh and retry");
        Zone from = zone(input.zoneFromId()); Zone to = zone(input.zoneToId());
        validateRule(input, from, to, id);
        rule.update(from, to, input.sizeBand(), money(input.baseFee()), money(input.perKg()), money(input.minFee()),
            input.maxWeightKg(), rule.getIncludedWeightKg(), rule.getExpressSurchargePercent(), input.active(),
            input.effectiveFrom(), input.effectiveTo());
        return PricingRuleView.of(rules.save(rule));
    }

    private Zone zone(UUID id) {
        return zones.findById(id).orElseThrow(() -> new PricingException(ErrorCode.RESOURCE_NOT_FOUND, "Referenced zone was not found"));
    }
    private void validateRule(PricingRuleInput input, Zone from, Zone to, UUID currentId) {
        if (input.effectiveTo() != null && !input.effectiveFrom().isBefore(input.effectiveTo()))
            throw new PricingException(ErrorCode.VALIDATION_FAILED, "effectiveFrom must be before effectiveTo");
        if (input.maxWeightKg().signum() <= 0 || input.baseFee().signum() < 0 || input.perKg().signum() < 0 || input.minFee().signum() < 0)
            throw new PricingException(ErrorCode.VALIDATION_FAILED, "Fees must be non-negative and maximum weight must be positive");
        if (input.active() && (!from.isActive() || !to.isActive()))
            throw new PricingException(ErrorCode.CONFLICT, "Active pricing rules must reference active zones");
        if (input.active() && !rules.findOverlaps(from.getId(), to.getId(), input.sizeBand(), input.effectiveFrom(),
                input.effectiveTo(), currentId == null ? new UUID(0, 0) : currentId).isEmpty())
            throw new PricingException(ErrorCode.CONFLICT, "An active pricing rule already overlaps this effective period");
    }
    private static BigDecimal money(BigDecimal amount) { return amount.setScale(2, java.math.RoundingMode.HALF_UP); }
}
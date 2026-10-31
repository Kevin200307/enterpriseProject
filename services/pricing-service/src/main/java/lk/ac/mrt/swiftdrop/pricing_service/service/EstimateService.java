package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.EstimateRequest;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.EstimateResponse;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import lk.ac.mrt.swiftdrop.pricing_service.repository.PricingRuleRepository;
import lk.ac.mrt.swiftdrop.pricing_service.repository.ZoneRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EstimateService {
    private final ZoneRepository zones;
    private final PricingRuleRepository rules;
    private final PricingEngine engine;
    private final Cache cache;
    public EstimateService(ZoneRepository zones, PricingRuleRepository rules, PricingEngine engine, CacheManager caches) {
        this.zones = zones; this.rules = rules; this.engine = engine; this.cache = caches.getCache("pricingEstimates");
    }

    @Transactional(readOnly = true)
    public EstimateResponse estimate(EstimateRequest request) { return estimateAt(request, Instant.now()); }

    @Transactional(readOnly = true)
    public EstimateResponse estimateAt(EstimateRequest request, Instant at) {
        if (request.weightKg().signum() <= 0) throw new PricingException(ErrorCode.VALIDATION_FAILED, "weightKg must be positive");
        Zone from = resolve(request.fromCity()); Zone to = resolve(request.toCity());
        List<PricingRule> effective = rules.findEffective(from.getId(), to.getId(), request.size(), at);
        if (effective.isEmpty()) throw new PricingException(ErrorCode.RESOURCE_NOT_FOUND,
            "No price is available for this route and package size.");
        if (effective.size() > 1) throw new PricingException(ErrorCode.CONFLICT, "More than one effective pricing rule applies to this route");
        PricingRule rule = effective.getFirst();
        if (request.weightKg().compareTo(rule.getMaxWeightKg()) > 0)
            throw new PricingException(ErrorCode.VALIDATION_FAILED, "weightKg exceeds the maximum for this pricing rule");
        String key = String.join("|", normalize(request.fromCity()), normalize(request.toCity()), request.size().name(),
            request.weightKg().stripTrailingZeros().toPlainString(), Boolean.toString(request.express()), rule.getId().toString(),
            Long.toString(rule.getVersion()), rule.getEffectiveFrom().toString(), String.valueOf(rule.getEffectiveTo()));
        Instant calculatedAt = at.truncatedTo(ChronoUnit.MINUTES);
        return cache.get(key, () -> calculate(from, to, rule, request, calculatedAt));
    }

    private EstimateResponse calculate(Zone from, Zone to, PricingRule rule, EstimateRequest request, Instant at) {
        PricingEngine.Result result = engine.calculate(new PricingContext(rule, request.weightKg(), request.express(), at));
        return new EstimateResponse(from.getCode(), to.getCode(), result.baseFee(), result.weightSurcharge(),
            result.expressSurcharge(), result.total(), "LKR", rule.getId(), at);
    }
    private Zone resolve(String city) {
        return zones.findActiveByNormalizedCity(normalize(city)).orElseThrow(() ->
            new PricingException(ErrorCode.RESOURCE_NOT_FOUND, "We don't deliver to " + city.trim() + " yet."));
    }
    private static String normalize(String city) { return city.trim().toLowerCase(Locale.ROOT); }
}
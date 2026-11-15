package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.util.*;
import java.util.Locale;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.ZoneInput;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.ZoneView;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import lk.ac.mrt.swiftdrop.pricing_service.domain.ZoneCity;
import lk.ac.mrt.swiftdrop.pricing_service.repository.PricingRuleRepository;
import lk.ac.mrt.swiftdrop.pricing_service.repository.ZoneRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ZoneService {
    private final ZoneRepository zones;
    private final PricingRuleRepository rules;
    public ZoneService(ZoneRepository zones, PricingRuleRepository rules) { this.zones = zones; this.rules = rules; }

    @Transactional(readOnly = true)
    public List<ZoneView> list(boolean includeInactive) {
        return (includeInactive ? zones.findAllByOrderByNameAsc() : zones.findByActiveTrueOrderByNameAsc())
            .stream().map(ZoneView::of).toList();
    }

    @Transactional(readOnly = true)
    public ZoneView resolve(String city) {
        Zone zone = zones.findActiveByNormalizedCity(normalize(city)).orElseThrow(() ->
            new PricingException(ErrorCode.RESOURCE_NOT_FOUND, "No active delivery zone is mapped to that city"));
        return ZoneView.of(zone);
    }

    @CacheEvict(cacheNames = "pricingEstimates", allEntries = true)
    public ZoneView create(ZoneInput input) {
        validateUniqueNames(input.name(), input.code(), null);
        return ZoneView.of(zones.save(new Zone(input.name().trim(), input.code(), cities(input.cities()), input.active())));
    }

    @CacheEvict(cacheNames = "pricingEstimates", allEntries = true)
    public ZoneView update(UUID id, ZoneInput input) {
        Zone zone = get(id);
        validateUniqueNames(input.name(), input.code(), id);
        if (zone.isActive() && !input.active() && rules.countByZoneFromIdOrZoneToId(id, id) > 0) {
            throw new PricingException(ErrorCode.CONFLICT, "Deactivate active pricing rules before deactivating this zone");
        }
        zone.update(input.name().trim(), input.code(), cities(input.cities()), input.active());
        return ZoneView.of(zones.save(zone));
    }

    @CacheEvict(cacheNames = "pricingEstimates", allEntries = true)
    public void deactivate(UUID id) {
        Zone zone = get(id);
        if (rules.countByZoneFromIdOrZoneToId(id, id) > 0) {
            throw new PricingException(ErrorCode.CONFLICT, "Deactivate pricing rules that reference this zone first");
        }
        zone.deactivate();
        zones.save(zone);
    }

    Zone get(UUID id) {
        return zones.findById(id).orElseThrow(() -> new PricingException(ErrorCode.RESOURCE_NOT_FOUND, "Zone was not found"));
    }

    private void validateUniqueNames(String name, String code, UUID currentId) {
        boolean duplicate = zones.findAll().stream().filter(zone -> currentId == null || !zone.getId().equals(currentId))
            .anyMatch(zone -> zone.getName().equalsIgnoreCase(name.trim()) || zone.getCode().equalsIgnoreCase(code));
        if (duplicate) throw new PricingException(ErrorCode.CONFLICT, "Zone name or code is already in use");
    }

    private static Set<ZoneCity> cities(List<String> values) {
        Set<ZoneCity> result = new LinkedHashSet<>();
        for (String value : values) {
            String trimmed = value.trim();
            if (!result.add(new ZoneCity(trimmed))) throw new PricingException(ErrorCode.VALIDATION_FAILED,
                "City names must be unique ignoring case and whitespace");
        }
        return result;
    }

    static String normalize(String city) {
        if (city == null || city.isBlank()) throw new PricingException(ErrorCode.VALIDATION_FAILED, "City must not be blank");
        return city.trim().toLowerCase(Locale.ROOT);
    }
}
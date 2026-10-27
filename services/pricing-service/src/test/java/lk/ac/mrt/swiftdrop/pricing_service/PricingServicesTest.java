package lk.ac.mrt.swiftdrop.pricing_service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.*;
import lk.ac.mrt.swiftdrop.pricing_service.domain.*;
import lk.ac.mrt.swiftdrop.pricing_service.repository.*;
import lk.ac.mrt.swiftdrop.pricing_service.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

class PricingServicesTest {
    private ZoneRepository zones;
    private PricingRuleRepository rules;
    private ZoneService zoneService;
    private PricingRuleService ruleService;
    private EstimateService estimateService;
    private Zone colombo;
    private Zone galle;
    private PricingRule rule;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

    private static BigDecimal d(String value) { return new BigDecimal(value); }

    @BeforeEach
    void setUp() {
        zones = mock(ZoneRepository.class);
        rules = mock(PricingRuleRepository.class);
        zoneService = new ZoneService(zones, rules);
        ruleService = new PricingRuleService(rules, zones, d("1.00"), d("15.00"));
        var engine = new PricingEngine(List.of(new ZoneBandStrategy(), new WeightSurchargeStrategy(), new ExpressSurchargeStrategy()));
        estimateService = new EstimateService(zones, rules, engine, new ConcurrentMapCacheManager("pricingEstimates"));
        colombo = mock(Zone.class); when(colombo.getId()).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000001"));
        when(colombo.getCode()).thenReturn("CMB"); when(colombo.isActive()).thenReturn(true);
        when(colombo.getCities()).thenReturn(Set.of(new ZoneCity("Colombo")));
        galle = mock(Zone.class); when(galle.getId()).thenReturn(UUID.fromString("10000000-0000-0000-0000-000000000005"));
        when(galle.getCode()).thenReturn("GAL"); when(galle.isActive()).thenReturn(true);
        when(galle.getCities()).thenReturn(Set.of(new ZoneCity("Galle")));
        rule = mock(PricingRule.class);
        when(rule.getId()).thenReturn(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        when(rule.getZoneFrom()).thenReturn(colombo); when(rule.getZoneTo()).thenReturn(galle);
        when(rule.getSizeBand()).thenReturn(PackageSize.MEDIUM);
        when(rule.getBaseFee()).thenReturn(d("650.00")); when(rule.getPerKg()).thenReturn(d("70.00"));
        when(rule.getMinFee()).thenReturn(d("650.00")); when(rule.getMaxWeightKg()).thenReturn(d("15.000"));
        when(rule.getIncludedWeightKg()).thenReturn(d("1.000")); when(rule.getExpressSurchargePercent()).thenReturn(d("15.00"));
        when(rule.getEffectiveFrom()).thenReturn(Instant.parse("2020-01-01T00:00:00Z")); when(rule.getVersion()).thenReturn(1L);
    }

    @Test
    void zoneListingResolutionAndCreationNormalizeCities() {
        when(zones.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(colombo));
        when(zones.findAllByOrderByNameAsc()).thenReturn(List.of(colombo));
        assertThat(zoneService.list(false)).hasSize(1);
        assertThat(zoneService.list(true)).hasSize(1);
        when(zones.findActiveByNormalizedCity("colombo")).thenReturn(Optional.of(colombo));
        assertThat(zoneService.resolve("  COLOMBO ")).isNotNull();
        when(zones.findAll()).thenReturn(List.of());
        when(zones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ZoneView created = zoneService.create(new ZoneInput("New Zone", "NEW", List.of("Negombo", "Wattala"), true));
        assertThat(created.name()).isEqualTo("New Zone");
        assertThat(created.cities()).containsExactlyInAnyOrder("Negombo", "Wattala");
        verify(zones).save(any(Zone.class));
    }

    @Test
    void zoneResolveUnknownDuplicateAndReferencedDeactivationFailClearly() {
        when(zones.findActiveByNormalizedCity(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> zoneService.resolve("Atlantis"))
            .isInstanceOf(PricingException.class).extracting("code").isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        when(zones.findAll()).thenReturn(List.of(colombo)); when(colombo.getName()).thenReturn("Colombo");
        when(colombo.getCode()).thenReturn("CMB");
        assertThatThrownBy(() -> zoneService.create(new ZoneInput("colombo", "XYZ", List.of("Other"), true)))
            .isInstanceOf(PricingException.class);
        when(zones.findById(colombo.getId())).thenReturn(Optional.of(colombo));
        when(rules.countByZoneFromIdOrZoneToId(colombo.getId(), colombo.getId())).thenReturn(1L);
        assertThatThrownBy(() -> zoneService.deactivate(colombo.getId())).isInstanceOf(PricingException.class);
    }

    @Test
    void zoneUpdateAndUnreferencedDeactivationSucceed() {
        Zone zone = new Zone("Colombo", "CMB", Set.of(new ZoneCity("Colombo")), true);
        when(zones.findById(colombo.getId())).thenReturn(Optional.of(zone));
        when(zones.findAll()).thenReturn(List.of(colombo));
        when(colombo.getName()).thenReturn("Colombo"); when(colombo.getCode()).thenReturn("CMB");
        when(rules.countByZoneFromIdOrZoneToId(colombo.getId(), colombo.getId())).thenReturn(0L);
        when(zones.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ZoneView updated = zoneService.update(colombo.getId(), new ZoneInput("Colombo City", "CMB", List.of("Colombo"), true));
        assertThat(updated.name()).isEqualTo("Colombo City");
        zoneService.deactivate(colombo.getId());
        verify(zones, times(2)).save(zone);
    }

    @Test
    void ruleCreationValidatesZonesPeriodsAndOverlap() {
        var input = new PricingRuleInput(colombo.getId(), galle.getId(), PackageSize.MEDIUM, d("650"), d("70"),
            d("650"), d("15"), true, now, null, null);
        when(zones.findById(colombo.getId())).thenReturn(Optional.of(colombo));
        when(zones.findById(galle.getId())).thenReturn(Optional.of(galle));
        when(rules.findOverlaps(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(rules.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        assertThat(ruleService.create(input)).isNotNull();
        when(rules.findOverlaps(any(), any(), any(), any(), any(), any())).thenReturn(List.of(rule));
        assertThatThrownBy(() -> ruleService.create(input)).isInstanceOf(PricingException.class);
        var invalidRange = new PricingRuleInput(colombo.getId(), galle.getId(), PackageSize.MEDIUM, d("1"), d("1"),
            d("1"), d("1"), false, now, now, null);
        assertThatThrownBy(() -> ruleService.create(invalidRange)).isInstanceOf(PricingException.class);
    }

    @Test
    void ruleUpdateChecksOptimisticVersionAndMissingZone() {
        UUID id = rule.getId();
        when(rules.findById(id)).thenReturn(Optional.of(rule));
        when(rule.getVersion()).thenReturn(3L);
        var input = new PricingRuleInput(colombo.getId(), galle.getId(), PackageSize.MEDIUM, d("650"), d("70"),
            d("650"), d("15"), true, now, null, 2L);
        assertThatThrownBy(() -> ruleService.update(id, input)).isInstanceOf(PricingException.class);
        when(rules.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleService.update(id, input)).isInstanceOf(PricingException.class);
        when(zones.findById(colombo.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> ruleService.create(input)).isInstanceOf(PricingException.class);
    }

    @Test
    void estimatesAreDeterministicAndRejectUnknownRoutesAndWeights() {
        when(zones.findActiveByNormalizedCity("colombo")).thenReturn(Optional.of(colombo));
        when(zones.findActiveByNormalizedCity("galle")).thenReturn(Optional.of(galle));
        when(rules.findEffective(colombo.getId(), galle.getId(), PackageSize.MEDIUM, now)).thenReturn(List.of(rule));
        var request = new EstimateRequest(" Colombo ", "Galle", PackageSize.MEDIUM, d("2.000"), true);
        EstimateResponse first = estimateService.estimateAt(request, now);
        EstimateResponse second = estimateService.estimateAt(request, now);
        assertThat(first).isEqualTo(second);
        assertThat(first.total()).isEqualByComparingTo("828.00");
        assertThat(first.currency()).isEqualTo("LKR");
        verify(rules, times(2)).findEffective(colombo.getId(), galle.getId(), PackageSize.MEDIUM, now);
        when(zones.findActiveByNormalizedCity("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> estimateService.estimateAt(new EstimateRequest("Unknown", "Galle", PackageSize.SMALL, d("1"), false), now))
            .isInstanceOf(PricingException.class);
        when(rules.findEffective(colombo.getId(), galle.getId(), PackageSize.MEDIUM, now)).thenReturn(List.of());
        assertThatThrownBy(() -> estimateService.estimateAt(request, now)).isInstanceOf(PricingException.class);
        when(rules.findEffective(colombo.getId(), galle.getId(), PackageSize.MEDIUM, now)).thenReturn(List.of(rule, rule));
        assertThatThrownBy(() -> estimateService.estimateAt(request, now)).isInstanceOf(PricingException.class);
        when(rule.getMaxWeightKg()).thenReturn(d("1.000"));
        assertThatThrownBy(() -> estimateService.estimateAt(request, now)).isInstanceOf(PricingException.class);
    }
}
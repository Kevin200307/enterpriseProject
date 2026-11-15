package lk.ac.mrt.swiftdrop.pricing_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PricingRuleRepository extends JpaRepository<PricingRule, UUID> {
    List<PricingRule> findAllByOrderByEffectiveFromDesc();
    List<PricingRule> findByActiveTrueOrderByEffectiveFromDesc();

    @Query("select r from PricingRule r where r.zoneFrom.id = :fromId and r.zoneTo.id = :toId " +
            "and r.sizeBand = :size and r.active = true and r.effectiveFrom <= :at " +
            "and (r.effectiveTo is null or r.effectiveTo > :at) order by r.effectiveFrom desc")
    List<PricingRule> findEffective(@Param("fromId") UUID fromId, @Param("toId") UUID toId,
            @Param("size") PackageSize size, @Param("at") Instant at);

    @Query("select r from PricingRule r where r.zoneFrom.id = :fromId and r.zoneTo.id = :toId " +
            "and r.sizeBand = :size and r.active = true and r.id <> :excludeId " +
            "and (:to is null or r.effectiveFrom < :to) and (r.effectiveTo is null or r.effectiveTo > :from)")
    List<PricingRule> findOverlaps(@Param("fromId") UUID fromId, @Param("toId") UUID toId,
            @Param("size") PackageSize size, @Param("from") Instant from, @Param("to") Instant to,
            @Param("excludeId") UUID excludeId);

    long countByZoneFromIdOrZoneToId(UUID zoneFromId, UUID zoneToId);

    Optional<PricingRule> findFirstByZoneFromIdAndZoneToIdAndSizeBandAndActiveTrueOrderByEffectiveFromDesc(
            UUID zoneFromId, UUID zoneToId, PackageSize sizeBand);
}
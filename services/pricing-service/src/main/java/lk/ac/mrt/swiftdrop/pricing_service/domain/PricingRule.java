package lk.ac.mrt.swiftdrop.pricing_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;

@Entity
@Table(name = "pricing_rules")
public class PricingRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_from_id", nullable = false)
    private Zone zoneFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "zone_to_id", nullable = false)
    private Zone zoneTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "size_band", nullable = false, length = 16)
    private PackageSize sizeBand;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal perKg;

    @Column(name = "min_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal minFee;

    @Column(name = "max_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal maxWeightKg;

    @Column(name = "included_weight_kg", nullable = false, precision = 10, scale = 3)
    private BigDecimal includedWeightKg;

    @Column(name = "express_surcharge_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal expressSurchargePercent;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PricingRule() {}

    public PricingRule(Zone zoneFrom, Zone zoneTo, PackageSize sizeBand, BigDecimal baseFee,
            BigDecimal perKg, BigDecimal minFee, BigDecimal maxWeightKg, BigDecimal includedWeightKg,
            BigDecimal expressSurchargePercent, boolean active, Instant effectiveFrom, Instant effectiveTo) {
        this.zoneFrom = zoneFrom;
        this.zoneTo = zoneTo;
        this.sizeBand = sizeBand;
        this.baseFee = baseFee;
        this.perKg = perKg;
        this.minFee = minFee;
        this.maxWeightKg = maxWeightKg;
        this.includedWeightKg = includedWeightKg;
        this.expressSurchargePercent = expressSurchargePercent;
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public Zone getZoneFrom() { return zoneFrom; }
    public Zone getZoneTo() { return zoneTo; }
    public PackageSize getSizeBand() { return sizeBand; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getPerKg() { return perKg; }
    public BigDecimal getMinFee() { return minFee; }
    public BigDecimal getMaxWeightKg() { return maxWeightKg; }
    public BigDecimal getIncludedWeightKg() { return includedWeightKg; }
    public BigDecimal getExpressSurchargePercent() { return expressSurchargePercent; }
    public boolean isActive() { return active; }
    public Instant getEffectiveFrom() { return effectiveFrom; }
    public Instant getEffectiveTo() { return effectiveTo; }
    public long getVersion() { return version; }

    public void update(Zone zoneFrom, Zone zoneTo, PackageSize sizeBand, BigDecimal baseFee,
            BigDecimal perKg, BigDecimal minFee, BigDecimal maxWeightKg, BigDecimal includedWeightKg,
            BigDecimal expressSurchargePercent, boolean active, Instant effectiveFrom, Instant effectiveTo) {
        this.zoneFrom = zoneFrom;
        this.zoneTo = zoneTo;
        this.sizeBand = sizeBand;
        this.baseFee = baseFee;
        this.perKg = perKg;
        this.minFee = minFee;
        this.maxWeightKg = maxWeightKg;
        this.includedWeightKg = includedWeightKg;
        this.expressSurchargePercent = expressSurchargePercent;
        this.active = active;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }
}
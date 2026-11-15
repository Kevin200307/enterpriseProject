package lk.ac.mrt.swiftdrop.pricing_service.service;

import java.math.BigDecimal;
import java.time.Instant;
import lk.ac.mrt.swiftdrop.pricing_service.domain.PricingRule;

public record PricingContext(PricingRule rule, BigDecimal weightKg, boolean express, Instant effectiveAt) {}
package lk.ac.mrt.swiftdrop.pricing_service.service;

public interface PricingStrategy {
    int order();
    PricingBreakdown apply(PricingContext context, PricingBreakdown current);
}
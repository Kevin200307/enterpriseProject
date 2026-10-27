package lk.ac.mrt.swiftdrop.pricing_service.api;

import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.EstimateRequest;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.EstimateResponse;
import lk.ac.mrt.swiftdrop.pricing_service.service.EstimateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing")
public class EstimateController {
    private final EstimateService estimates;
    public EstimateController(EstimateService estimates) { this.estimates = estimates; }

    @PostMapping("/estimate")
    @PreAuthorize("isAuthenticated()")
    public EstimateResponse estimate(@Valid @RequestBody EstimateRequest request) { return estimates.estimate(request); }
}
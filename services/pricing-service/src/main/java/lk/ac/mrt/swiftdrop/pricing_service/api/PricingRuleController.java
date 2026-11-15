package lk.ac.mrt.swiftdrop.pricing_service.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.PricingRuleInput;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.PricingRuleView;
import lk.ac.mrt.swiftdrop.pricing_service.service.PricingRuleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing/rules")
public class PricingRuleController {
    private final PricingRuleService rules;
    public PricingRuleController(PricingRuleService rules) { this.rules = rules; }

    @GetMapping
    @PreAuthorize("!#includeInactive or hasRole('ADMIN')")
    public List<PricingRuleView> list(@RequestParam(defaultValue = "false") boolean includeInactive) { return rules.list(includeInactive); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PricingRuleView> create(@Valid @RequestBody PricingRuleInput input) {
        PricingRuleView created = rules.create(input);
        return ResponseEntity.created(URI.create("/api/v1/pricing/rules/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PricingRuleView update(@PathVariable UUID id, @Valid @RequestBody PricingRuleInput input) { return rules.update(id, input); }
}
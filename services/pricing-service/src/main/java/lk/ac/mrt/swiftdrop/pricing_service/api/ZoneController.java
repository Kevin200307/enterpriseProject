package lk.ac.mrt.swiftdrop.pricing_service.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.ZoneInput;
import lk.ac.mrt.swiftdrop.pricing_service.api.PricingDtos.ZoneView;
import lk.ac.mrt.swiftdrop.pricing_service.service.ZoneService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pricing/zones")
public class ZoneController {
    private final ZoneService zones;
    public ZoneController(ZoneService zones) { this.zones = zones; }

    @GetMapping
    @PreAuthorize("!#includeInactive or hasRole('ADMIN')")
    public List<ZoneView> list(@RequestParam(defaultValue = "false") boolean includeInactive) { return zones.list(includeInactive); }

    @GetMapping("/resolve")
    public ZoneView resolve(@RequestParam String city) { return zones.resolve(city); }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ZoneView> create(@Valid @RequestBody ZoneInput input) {
        ZoneView created = zones.create(input);
        return ResponseEntity.created(URI.create("/api/v1/pricing/zones/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ZoneView update(@PathVariable UUID id, @Valid @RequestBody ZoneInput input) { return zones.update(id, input); }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) { zones.deactivate(id); return ResponseEntity.noContent().build(); }
}
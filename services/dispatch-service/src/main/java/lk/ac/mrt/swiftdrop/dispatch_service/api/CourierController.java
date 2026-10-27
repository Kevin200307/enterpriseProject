package lk.ac.mrt.swiftdrop.dispatch_service.api;

import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.api.DispatchDtos.*;
import lk.ac.mrt.swiftdrop.dispatch_service.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/couriers")
public class CourierController {
    private final CourierService couriers;
    private final DispatchViews views;
    public CourierController(CourierService couriers, DispatchViews views) { this.couriers=couriers; this.views=views; }
    @GetMapping @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public PageView<Courier> list(@RequestParam(required=false) String zone, @RequestParam(required=false) VehicleType vehicle,
            @RequestParam(required=false) Boolean active, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return couriers.list(zone,vehicle,active,page,size);
    }
    @PostMapping @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public ResponseEntity<Courier> create(@Valid @RequestBody CourierInput input) {
        var courier = couriers.create(input);
        return ResponseEntity.created(URI.create("/api/v1/couriers/"+courier.id())).body(courier);
    }
    @PatchMapping("/{id}") @PreAuthorize("hasAnyRole('DISPATCHER','ADMIN')")
    public Courier patch(@PathVariable UUID id, @Valid @RequestBody CourierPatch input) { return couriers.patch(id,input); }
    @GetMapping("/me/assignments") @PreAuthorize("hasAnyRole('COURIER','DISPATCHER','ADMIN')")
    public List<Assignment> mine(Authentication auth) { return views.mine(UUID.fromString(auth.getName())); }
    @GetMapping("/{id}/manifest") @PreAuthorize("hasAnyRole('COURIER','DISPATCHER','ADMIN')")
    public List<Assignment> manifest(@PathVariable UUID id, @RequestParam LocalDate date, Authentication auth) {
        boolean manager = auth.getAuthorities().stream().anyMatch(a -> Set.of("ROLE_ADMIN","ROLE_DISPATCHER").contains(a.getAuthority()));
        return views.manifest(id,date,UUID.fromString(auth.getName()),manager);
    }
}

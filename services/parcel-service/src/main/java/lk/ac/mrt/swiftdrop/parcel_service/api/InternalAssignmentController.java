package lk.ac.mrt.swiftdrop.parcel_service.api;

import jakarta.validation.Valid;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.parcel_service.service.InternalAssignmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/internal/parcels")
public class InternalAssignmentController {
    private final InternalAssignmentService service;
    public InternalAssignmentController(InternalAssignmentService service) { this.service=service; }
    @PutMapping("/{id}/assignment")
    @PreAuthorize("authentication.token.claims['client_id'] == 'dispatch-service' and authentication.token.claims['scope'] == 'dispatch.internal'")
    public InternalAssignmentService.Result assign(@PathVariable UUID id, @Valid @RequestBody InternalAssignmentService.Command command) {
        return service.assign(id,command);
    }
}

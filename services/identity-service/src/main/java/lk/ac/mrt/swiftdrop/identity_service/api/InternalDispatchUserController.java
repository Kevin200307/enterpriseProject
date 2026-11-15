package lk.ac.mrt.swiftdrop.identity_service.api;

import java.util.*;
import lk.ac.mrt.swiftdrop.identity_service.repository.UserRepository;
import lk.ac.mrt.swiftdrop.identity_service.error.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/internal/users")
public class InternalDispatchUserController {
    private final UserRepository users;
    public InternalDispatchUserController(UserRepository users) { this.users=users; }
    public record User(UUID id, String fullName, Set<String> roles, String status) {}
    @GetMapping("/{id}") @Transactional(readOnly=true)
    @PreAuthorize("authentication.token.claims['client_id'] == 'dispatch-service' and authentication.token.claims['scope'] == 'dispatch.internal'")
    public User user(@PathVariable UUID id) {
        var user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User was not found"));
        return new User(user.getId(),user.getFullName(),user.getRoles().stream().map(r -> r.getId()).collect(java.util.stream.Collectors.toSet()),user.getStatus().name());
    }
}

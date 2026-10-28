package lk.ac.mrt.swiftdrop.identity_service.api;

import lk.ac.mrt.swiftdrop.identity_service.api.dto.UserResponse;
import lk.ac.mrt.swiftdrop.identity_service.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Internal role grant used only after merchant approval. */
@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalMerchantRoleController {

    private final UserService userService;

    public InternalMerchantRoleController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{id}/merchant-role")
    @PreAuthorize("authentication.token.claims['client_id'] == 'merchant-service' and hasAuthority('SCOPE_merchant.internal')")
    public ResponseEntity<UserResponse> grantMerchantRole(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.grantMerchantRole(id));
    }
}

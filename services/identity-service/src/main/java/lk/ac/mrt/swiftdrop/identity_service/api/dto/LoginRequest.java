package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Immutable password login request body.
 */
public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed email address")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}

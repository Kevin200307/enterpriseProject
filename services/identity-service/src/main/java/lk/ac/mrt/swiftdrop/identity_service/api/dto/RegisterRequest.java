package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Immutable registration request body.
 * - Valid email format
 * - Password >= 8 characters with at least one letter and one digit
 * - Full name (non-blank)
 * - Phone in +94XXXXXXXXX format
 */
public record RegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a well-formed email address")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$",
                message = "Password must be at least 8 characters long and contain at least one letter and one digit"
        )
        String password,

        @NotBlank(message = "Full name is required")
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @NotBlank(message = "Phone number is required")
        @Pattern(
                regexp = "^\\+94[0-9]{9}$",
                message = "Phone number must be in Sri Lankan international format (+94XXXXXXXXX)"
        )
        String phone
) {
}

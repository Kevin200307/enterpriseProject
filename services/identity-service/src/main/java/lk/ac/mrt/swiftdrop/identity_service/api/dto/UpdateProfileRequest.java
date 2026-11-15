package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for updating the authenticated user's profile (name / phone).
 */
public record UpdateProfileRequest(
        @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
        String fullName,

        @Pattern(
                regexp = "^\\+94[0-9]{9}$",
                message = "Phone number must be in Sri Lankan international format (+94XXXXXXXXX)"
        )
        String phone
) {
}

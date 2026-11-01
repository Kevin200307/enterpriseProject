package lk.ac.mrt.swiftdrop.merchant_service.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MerchantRegistrationRequest(
        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = "Organization name must not exceed 255 characters")
        String orgName,

        @Size(max = 100, message = "Business registration number must not exceed 100 characters")
        String businessRegNo,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Contact email must be valid")
        @Size(max = 255, message = "Contact email must not exceed 255 characters")
        String contactEmail,

        @NotBlank(message = "Contact phone is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Contact phone must be a valid phone number")
        String contactPhone
) {
}

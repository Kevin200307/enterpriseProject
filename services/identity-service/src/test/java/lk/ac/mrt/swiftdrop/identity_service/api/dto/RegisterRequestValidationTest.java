package lk.ac.mrt.swiftdrop.identity_service.api.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Should pass validation for completely valid registration payload")
    void shouldPassValidRegistration() {
        RegisterRequest request = new RegisterRequest(
                "amandi.arangala@swiftdrop.lk",
                "SecurePass123",
                "Amandi Arangala",
                "+94771234567"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"invalid-email", "user@", "@domain.com", "plainaddress"})
    @DisplayName("Should fail validation for malformed email addresses")
    void shouldFailMalformedEmail(String email) {
        RegisterRequest request = new RegisterRequest(
                email,
                "SecurePass123",
                "Amandi Arangala",
                "+94771234567"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("email"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "short1",          // < 8 chars
            "alllettersnodigit", // no digit
            "1234567890",       // no letter
            ""                  // blank
    })
    @DisplayName("Should fail validation for passwords not meeting complexity rules (>=8 chars, letter + digit)")
    void shouldFailWeakPasswords(String password) {
        RegisterRequest request = new RegisterRequest(
                "valid@swiftdrop.lk",
                password,
                "Amandi Arangala",
                "+94771234567"
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "0771234567",       // missing +94 prefix
            "+1234567890",      // not +94
            "+947712345",       // too short
            "+9477123456789",   // too long
            "not-a-number"      // invalid characters
    })
    @DisplayName("Should fail validation for phone numbers not matching +94XXXXXXXXX")
    void shouldFailInvalidPhoneFormat(String phone) {
        RegisterRequest request = new RegisterRequest(
                "valid@swiftdrop.lk",
                "SecurePass123",
                "Amandi Arangala",
                phone
        );

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }
}

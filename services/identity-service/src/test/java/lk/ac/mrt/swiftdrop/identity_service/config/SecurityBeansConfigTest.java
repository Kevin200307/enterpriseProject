package lk.ac.mrt.swiftdrop.identity_service.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityBeansConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        SecurityBeansConfig config = new SecurityBeansConfig();
        passwordEncoder = config.passwordEncoder();
    }

    @Test
    @DisplayName("Should encode password using BCrypt with strength 10")
    void shouldEncodePasswordWithBcryptStrength10() {
        String rawPassword = "StrongPassword123";
        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(encoded).isNotBlank();
        // $2a$, $2b$, or $2y$ followed by cost factor 10$ and 53 chars of salt+hash
        assertThat(encoded).matches("^\\$2[aby]\\$10\\$.{53}$");
    }

    @Test
    @DisplayName("Should correctly verify valid and invalid passwords")
    void shouldMatchPasswordsCorrectly() {
        String rawPassword = "CorrectPassword123";
        String encoded = passwordEncoder.encode(rawPassword);

        assertThat(passwordEncoder.matches("CorrectPassword123", encoded)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", encoded)).isFalse();
        assertThat(passwordEncoder.matches("correctpassword123", encoded)).isFalse(); // Case sensitive
    }

    @Test
    @DisplayName("Should generate different salt hashes for identical raw passwords")
    void shouldGenerateUniqueSaltsForSamePassword() {
        String rawPassword = "SamePassword123";

        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(passwordEncoder.matches(rawPassword, hash1)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, hash2)).isTrue();
    }
}

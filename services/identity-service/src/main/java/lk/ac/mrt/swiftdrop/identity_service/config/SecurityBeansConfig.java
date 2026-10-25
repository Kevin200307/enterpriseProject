package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;

/**
 * Declares core cryptographic security beans.
 * Decoupled from SecurityFilterChain configuration to prevent circular dependency cycles.
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * BCrypt cost/work factor 
     * 2^10 (1024) hashing rounds providing strong resistance against brute-force attacks.
     */
    public static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH, new SecureRandom());
    }
}

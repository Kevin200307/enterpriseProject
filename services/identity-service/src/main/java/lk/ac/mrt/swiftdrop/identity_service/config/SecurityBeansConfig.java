package lk.ac.mrt.swiftdrop.identity_service.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.SecureRandom;

/**
 * Declares core cryptographic security beans.
 * Decoupled from SecurityFilterChain configuration to prevent circular dependency cycles.
 */
@Configuration
public class SecurityBeansConfig {

    /**
     * BCrypt cost/work factor.
     * 2^10 (1024) hashing rounds providing strong resistance against brute-force attacks.
     */
    public static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH, new SecureRandom());
    }

    @Bean
    @ConditionalOnMissingBean(JwtEncoder.class)
    public JwtEncoder jwtEncoder(RsaKeyProvider rsaKeyProvider) {
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKeyProvider.toRsaJwk()));
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder(RsaKeyProvider rsaKeyProvider) {
        return NimbusJwtDecoder.withPublicKey(rsaKeyProvider.getPublicKey()).build();
    }
}

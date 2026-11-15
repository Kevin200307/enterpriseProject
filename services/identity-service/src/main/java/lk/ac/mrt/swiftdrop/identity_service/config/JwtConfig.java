package lk.ac.mrt.swiftdrop.identity_service.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {
    @Bean
    RSAKey jwtRsaKey(JwtProperties properties) {
        if (blank(properties.privateKeyPem()) || blank(properties.publicKeyPem())) {
            throw new IllegalStateException("IDENTITY_JWT_PRIVATE_KEY and IDENTITY_JWT_PUBLIC_KEY are required");
        }
        try {
            var factory = KeyFactory.getInstance("RSA");
            var publicKey = (RSAPublicKey) factory.generatePublic(new X509EncodedKeySpec(decode(properties.publicKeyPem())));
            var privateKey = (RSAPrivateKey) factory.generatePrivate(new PKCS8EncodedKeySpec(decode(properties.privateKeyPem())));
            return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(properties.keyId()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid identity RSA key configuration", ex);
        }
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey key) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(key)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey key, JwtProperties properties) throws Exception {
        var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
        var issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        var audience = new JwtClaimValidator<java.util.List<String>>("aud",
                values -> values != null && values.contains(properties.audience()));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audience));
        return decoder;
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private static byte[] decode(String pem) {
        String normalized = pem.replace("\\n", "\n")
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(normalized);
    }
}

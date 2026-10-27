package lk.ac.mrt.swiftdrop.identity_service.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * Production implementation of RsaKeyProvider.
 * Loads RS256 private and public keys from PEM environment variables.
 * In development/test mode without configured keys, generates an ephemeral 2048-bit key pair.
 * In production profiles ('prod', 'production'), strictly prohibits throwaway keys.
 */
@Component
public class RsaKeyProviderImpl implements RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProviderImpl.class);
    private static final int RSA_KEY_SIZE = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;
    private final KeyPair keyPair;
    private final String keyId;
    private final RSAKey rsaJwk;

    public RsaKeyProviderImpl(JwtProperties jwtProperties, Environment environment) {
        this.keyId = jwtProperties.keyId();

        String privatePem = jwtProperties.privateKeyPem();
        String publicPem = jwtProperties.publicKeyPem();

        if (hasText(privatePem)) {
            // Load from provided PEM strings
            KeyPair loadedPair = loadKeysFromPem(privatePem, publicPem);
            this.keyPair = loadedPair;
            this.privateKey = (RSAPrivateKey) loadedPair.getPrivate();
            this.publicKey = (RSAPublicKey) loadedPair.getPublic();
            log.info("Successfully loaded RS256 key pair from configured PEM properties [keyId={}]", keyId);
        } else {
            // Check production constraint: throwaway keys are forbidden in production
            if (isProductionEnvironment(environment)) {
                throw new IllegalStateException(
                        "Cannot start identity-service in production mode without configured IDENTITY_JWT_PRIVATE_KEY and IDENTITY_JWT_PUBLIC_KEY. " +
                                "Throwaway keys are strictly forbidden."
                );
            }

            // In dev/test: generate ephemeral 2048-bit RS256 key pair
            log.warn("IDENTITY_JWT_PRIVATE_KEY is not configured. Generating ephemeral 2048-bit RS256 key pair for dev/test mode [keyId={}]. Never use throwaway keys in production!", keyId);
            KeyPair generatedPair = generateEphemeralKeyPair();
            this.keyPair = generatedPair;
            this.privateKey = (RSAPrivateKey) generatedPair.getPrivate();
            this.publicKey = (RSAPublicKey) generatedPair.getPublic();
        }

        // Build Nimbus RSAKey for cryptographic signing and RFC 7517 JWKS serialization
        this.rsaJwk = new RSAKey.Builder(this.publicKey)
                .privateKey(this.privateKey)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .keyID(this.keyId)
                .build();
    }

    @Override
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    @Override
    public KeyPair getKeyPair() {
        return keyPair;
    }

    @Override
    public String getKeyId() {
        return keyId;
    }

    @Override
    public RSAKey toRsaJwk() {
        return rsaJwk;
    }

    private KeyPair loadKeysFromPem(String privatePem, String publicPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            // Parse PKCS#8 private key
            byte[] privateBytes = decodePem(privatePem);
            PKCS8EncodedKeySpec privateSpec = new PKCS8EncodedKeySpec(privateBytes);
            RSAPrivateKey privKey = (RSAPrivateKey) keyFactory.generatePrivate(privateSpec);

            // Parse or derive public key
            RSAPublicKey pubKey;
            if (hasText(publicPem)) {
                byte[] publicBytes = decodePem(publicPem);
                X509EncodedKeySpec publicSpec = new X509EncodedKeySpec(publicBytes);
                pubKey = (RSAPublicKey) keyFactory.generatePublic(publicSpec);
            } else if (privKey instanceof RSAPrivateCrtKey crtKey) {
                // If public key is omitted but private key contains CRT parameters, derive public key
                RSAPublicKeySpec publicSpec = new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
                pubKey = (RSAPublicKey) keyFactory.generatePublic(publicSpec);
                log.info("Derived RSAPublicKey from RSAPrivateCrtKey parameters");
            } else {
                throw new IllegalArgumentException("Public key PEM was not provided and cannot be derived from private key");
            }

            return new KeyPair(pubKey, privKey);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalArgumentException("Failed to parse RSA keys from PEM data: " + ex.getMessage(), ex);
        }
    }

    private byte[] decodePem(String pem) {
        String cleaned = pem
                .replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        return Base64.getDecoder().decode(cleaned);
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("RSA algorithm not supported on this platform", ex);
        }
    }

    private boolean isProductionEnvironment(Environment environment) {
        if (environment == null) return false;
        String[] activeProfiles = environment.getActiveProfiles();
        return Arrays.stream(activeProfiles)
                .anyMatch(profile -> "prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile));
    }

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }
}

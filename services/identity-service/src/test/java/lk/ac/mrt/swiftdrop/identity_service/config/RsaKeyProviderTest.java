package lk.ac.mrt.swiftdrop.identity_service.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RsaKeyProviderTest {

    @Test
    @DisplayName("Should generate ephemeral 2048-bit RS256 key pair when keys are not configured in dev/test")
    void shouldGenerateEphemeralKeyPairWhenNoKeysConfiguredInDev() {
        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                "",
                "",
                "test-key-id"
        );
        MockEnvironment environment = new MockEnvironment();

        RsaKeyProvider provider = new RsaKeyProviderImpl(properties, environment);

        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(provider.getPrivateKey()).isNotNull();
        assertThat(provider.getKeyPair()).isNotNull();
        assertThat(provider.getKeyId()).isEqualTo("test-key-id");

        // Verify 2048-bit RSA key
        assertThat(provider.getPublicKey().getAlgorithm()).isEqualTo("RSA");
        assertThat(provider.getPublicKey().getModulus().bitLength()).isEqualTo(2048);

        // Verify Nimbus RSAKey representation
        RSAKey jwk = provider.toRsaJwk();
        assertThat(jwk).isNotNull();
        assertThat(jwk.getKeyID()).isEqualTo("test-key-id");
        assertThat(jwk.getKeyUse()).isEqualTo(KeyUse.SIGNATURE);
        assertThat(jwk.getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwk.isPrivate()).isTrue();
    }

    @Test
    @DisplayName("Should load and parse keys from valid PKCS#8 private and X.509 public PEM strings")
    void shouldLoadKeysFromValidPemStrings() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair originalKeyPair = generator.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(originalKeyPair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        String publicPem = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(originalKeyPair.getPublic().getEncoded()) +
                "\n-----END PUBLIC KEY-----";

        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                privatePem,
                publicPem,
                "swiftdrop-identity-key-1"
        );
        MockEnvironment environment = new MockEnvironment();

        RsaKeyProvider provider = new RsaKeyProviderImpl(properties, environment);

        assertThat(provider.getPublicKey()).isEqualTo(originalKeyPair.getPublic());
        assertThat(provider.getPrivateKey()).isEqualTo(originalKeyPair.getPrivate());

        // Cryptographic signature and verification sanity check
        byte[] payload = "test-jwt-header.test-jwt-payload".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(provider.getPrivateKey());
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(provider.getPublicKey());
        verifier.update(payload);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    @DisplayName("Should derive RSAPublicKey from RSAPrivateCrtKey when public key PEM is omitted")
    void shouldDerivePublicKeyWhenOnlyPrivateKeyPemIsConfigured() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair originalKeyPair = generator.generateKeyPair();

        String privatePem = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getEncoder().encodeToString(originalKeyPair.getPrivate().getEncoded()) +
                "\n-----END PRIVATE KEY-----";

        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                privatePem,
                null, // Public key omitted
                "derived-key-id"
        );
        MockEnvironment environment = new MockEnvironment();

        RsaKeyProvider provider = new RsaKeyProviderImpl(properties, environment);

        assertThat(provider.getPublicKey()).isNotNull();
        assertThat(provider.getPublicKey().getModulus()).isEqualTo(((RSAPrivateKey) originalKeyPair.getPrivate()).getModulus());

        // Validate signing and verification with derived key
        byte[] payload = "data-to-sign".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(provider.getPrivateKey());
        signer.update(payload);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(provider.getPublicKey());
        verifier.update(payload);
        assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    @DisplayName("Should correctly handle escaped newlines in PEM string (e.g., from Docker Compose env)")
    void shouldHandleEscapedNewlinesInPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair originalKeyPair = generator.generateKeyPair();

        String base64Private = Base64.getEncoder().encodeToString(originalKeyPair.getPrivate().getEncoded());
        String escapedPem = "-----BEGIN PRIVATE KEY-----\\n" + base64Private + "\\n-----END PRIVATE KEY-----";

        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                escapedPem,
                "",
                "escaped-newline-key"
        );
        MockEnvironment environment = new MockEnvironment();

        RsaKeyProvider provider = new RsaKeyProviderImpl(properties, environment);

        assertThat(provider.getPrivateKey()).isEqualTo(originalKeyPair.getPrivate());
    }

    @Test
    @DisplayName("Should throw IllegalStateException when keys are missing in production profile")
    void shouldThrowWhenKeysMissingInProduction() {
        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                "",
                "",
                "prod-key"
        );
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");

        assertThatThrownBy(() -> new RsaKeyProviderImpl(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot start identity-service in production mode without configured IDENTITY_JWT_PRIVATE_KEY")
                .hasMessageContaining("Throwaway keys are strictly forbidden");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when PEM content is malformed")
    void shouldThrowOnMalformedPem() {
        JwtProperties properties = new JwtProperties(
                "http://identity-service:8080",
                900,
                604800,
                "-----BEGIN PRIVATE KEY-----\nNOT_VALID_BASE64!@#$%\n-----END PRIVATE KEY-----",
                "",
                "bad-key"
        );
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new RsaKeyProviderImpl(properties, environment))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

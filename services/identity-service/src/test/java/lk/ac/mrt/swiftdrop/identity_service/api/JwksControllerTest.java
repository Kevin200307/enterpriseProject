package lk.ac.mrt.swiftdrop.identity_service.api;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = JwksController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class JwksControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RsaKeyProvider rsaKeyProvider;

    @Test
    @DisplayName("GET /.well-known/jwks.json - 200 OK public, cacheable RFC 7517 JWKS with no private parameters")
    void shouldReturnPublicJwks() throws Exception {
        // Setup realistic 2048-bit RSA key pair
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        String keyId = "swiftdrop-identity-key-1";
        RSAKey fullRsaJwk = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        when(rsaKeyProvider.toRsaJwk()).thenReturn(fullRsaJwk);

        MvcResult result = mockMvc.perform(get("/.well-known/jwks.json")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=86400")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("public")))
                .andExpect(jsonPath("$.keys", hasSize(1)))
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value(keyId))
                .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
                .andExpect(jsonPath("$.keys[0].n").isNotEmpty())
                // Critical security check: private key parameters MUST NEVER be exposed
                .andExpect(jsonPath("$.keys[0].d").doesNotExist())
                .andExpect(jsonPath("$.keys[0].p").doesNotExist())
                .andExpect(jsonPath("$.keys[0].q").doesNotExist())
                .andExpect(jsonPath("$.keys[0].dp").doesNotExist())
                .andExpect(jsonPath("$.keys[0].dq").doesNotExist())
                .andExpect(jsonPath("$.keys[0].qi").doesNotExist())
                .andReturn();

        // Verify that downstream resource services can parse and verify signatures with the exposed JWKS
        String responseContent = result.getResponse().getContentAsString();
        JWKSet parsedJwkSet = JWKSet.parse(responseContent);
        assertThat(parsedJwkSet.getKeys()).hasSize(1);

        com.nimbusds.jose.jwk.JWK parsedJwk = parsedJwkSet.getKeyByKeyId(keyId);
        assertThat(parsedJwk).isNotNull();
        assertThat(parsedJwk).isInstanceOf(RSAKey.class);

        RSAKey parsedRsaKey = (RSAKey) parsedJwk;
        RSAPublicKey parsedPublicKey = parsedRsaKey.toRSAPublicKey();

        // Cryptographic proof: sign with private key and verify with the public key from the JWKS endpoint
        byte[] testData = "jwt.header.payload".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(testData);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(parsedPublicKey);
        verifier.update(testData);
        assertThat(verifier.verify(signature)).isTrue();
    }
}

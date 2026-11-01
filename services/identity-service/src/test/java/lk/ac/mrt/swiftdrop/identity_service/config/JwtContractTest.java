package lk.ac.mrt.swiftdrop.identity_service.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.api.JwksController;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtTokenService;
import org.junit.jupiter.api.Test;

class JwtContractTest {
    @Test
    void signsAndPublishesTheSharedTokenContract() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var properties = new JwtProperties(
                "https://identity.test", "swiftdrop", 900, "test-key",
                pem("PRIVATE KEY", ((RSAPrivateKey) pair.getPrivate()).getEncoded()),
                pem("PUBLIC KEY", ((RSAPublicKey) pair.getPublic()).getEncoded()));
        var config = new JwtConfig();
        var rsaKey = config.jwtRsaKey(properties);
        var tokens = new JwtTokenService(config.jwtEncoder(rsaKey), properties);

        var user = new User("user@swiftdrop.lk", "hash", "Test User", "+94771234567");
        user.setId(UUID.randomUUID());
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setRoles(Set.of(new RoleEntity(Role.CUSTOMER, "Customer")));

        String encoded = tokens.createAccessToken(user);
        var decoded = config.jwtDecoder(rsaKey, properties).decode(encoded);

        assertThat(decoded.getHeaders()).containsEntry("kid", "test-key");
        assertThat(decoded.getIssuer().toString()).isEqualTo("https://identity.test");
        assertThat(decoded.getAudience()).containsExactly("swiftdrop");
        assertThat(decoded.getSubject()).isEqualTo(user.getId().toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
        assertThat(tokens.expiresIn()).isEqualTo(900);

        var serviceToken = config.jwtDecoder(rsaKey,properties).decode(tokens.createServiceToken(
            lk.ac.mrt.swiftdrop.identity_service.api.DispatchServiceTokenController.DISPATCH_SUBJECT,"dispatch.internal"));
        assertThat(serviceToken.getClaimAsString("client_id")).isEqualTo("dispatch-service");
        assertThat(serviceToken.getClaimAsString("scope")).isEqualTo("dispatch.internal");
        assertThat(serviceToken.getClaimAsStringList("roles")).isEmpty();
        assertThat(java.time.Duration.between(serviceToken.getIssuedAt(),serviceToken.getExpiresAt()).toSeconds()).isEqualTo(300);

        var jwks = new JwksController(rsaKey).jwks().getBody();
        assertThat(jwks).isNotNull();
        @SuppressWarnings("unchecked")
        var published = (java.util.Map<String, Object>) ((java.util.List<?>) jwks.get("keys")).getFirst();
        assertThat(published).containsEntry("kid", "test-key").containsEntry("kty", "RSA");
        assertThat(published).containsKeys("n", "e").doesNotContainKeys("d", "p", "q");
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}

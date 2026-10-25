package lk.ac.mrt.swiftdrop.identity_service.api;

import com.nimbusds.jose.jwk.JWKSet;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.ac.mrt.swiftdrop.identity_service.config.RsaKeyProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;

/**
 * Public RFC 7517 JSON Web Key Set (JWKS) endpoint.
 * Exposes the active RS256 public key(s) so downstream resource services
 * can independently verify SwiftDrop access token JWT signatures without shared secrets.
 */
@RestController
@Tag(name = "JWKS", description = "Public JSON Web Key Set for verifying RS256 JWT signatures")
public class JwksController {

    private final RsaKeyProvider rsaKeyProvider;

    public JwksController(RsaKeyProvider rsaKeyProvider) {
        this.rsaKeyProvider = rsaKeyProvider;
    }

    @GetMapping(path = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get public JWKS",
            description = "Returns the public JSON Web Key Set (RFC 7517) containing RS256 public keys used to verify SwiftDrop JWT access tokens. Unauthenticated and cacheable."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Public JWKS successfully retrieved",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
    })
    public ResponseEntity<Map<String, Object>> getJwks() {
        JWKSet jwkSet = new JWKSet(rsaKeyProvider.toRsaJwk().toPublicJWK());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(jwkSet.toJSONObject());
    }
}

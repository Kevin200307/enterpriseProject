package lk.ac.mrt.swiftdrop.identity_service.api;

import com.nimbusds.jose.jwk.RSAKey;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JwksController {
    private final RSAKey key;

    public JwksController(RSAKey key) { this.key = key; }

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePublic())
                .body(Map.of("keys", java.util.List.of(key.toPublicJWK().toJSONObject())));
    }
}

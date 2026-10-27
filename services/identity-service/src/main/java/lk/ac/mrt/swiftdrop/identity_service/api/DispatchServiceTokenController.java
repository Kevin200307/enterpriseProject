package lk.ac.mrt.swiftdrop.identity_service.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

/** Narrow service credential grant. Tokens carry an internal scope and no human roles. */
@RestController
public class DispatchServiceTokenController {
    public static final UUID SUBJECT = UUID.fromString("dee3070e-c6f4-4221-a042-17b9959d4095");
    private final String secret;
    private final JwtTokenService tokens;
    public DispatchServiceTokenController(@Value("${DISPATCH_CLIENT_SECRET:}") String secret, JwtTokenService tokens) {
        this.secret=secret; this.tokens=tokens;
    }
    public record Token(String access_token, String token_type, long expires_in) {}
    @PostMapping(value="/api/v1/auth/service-token",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Token> token(@RequestHeader(value="Authorization",defaultValue="") String authorization,
            @RequestParam String grant_type) {
        byte[] supplied;
        try {
            if (!authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            supplied = Base64.getDecoder().decode(authorization.substring(6));
        } catch (IllegalArgumentException ex) { throw new BadCredentialsException("Invalid service credentials"); }
        byte[] expected = ("dispatch-service:"+secret).getBytes(StandardCharsets.UTF_8);
        if (secret.length()<32 || !"client_credentials".equals(grant_type) || !MessageDigest.isEqual(expected,supplied))
            throw new BadCredentialsException("Invalid service credentials");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new Token(tokens.createServiceToken(SUBJECT,"dispatch.internal"),"Bearer",300));
    }
}

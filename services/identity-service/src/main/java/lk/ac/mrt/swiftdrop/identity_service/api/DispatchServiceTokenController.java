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
    public static final UUID DISPATCH_SUBJECT = UUID.fromString("dee3070e-c6f4-4221-a042-17b9959d4095");
    public static final UUID MERCHANT_SUBJECT = UUID.fromString("f1a2b3c4-d5e6-7890-abcd-ef1234567890");

    private final String dispatchSecret;
    private final String merchantSecret;
    private final JwtTokenService tokens;

    public DispatchServiceTokenController(
            @Value("${DISPATCH_CLIENT_SECRET:}") String dispatchSecret,
            @Value("${MERCHANT_CLIENT_SECRET:}") String merchantSecret,
            JwtTokenService tokens) {
        this.dispatchSecret = dispatchSecret;
        this.merchantSecret = merchantSecret;
        this.tokens = tokens;
    }

    public record Token(String access_token, String token_type, long expires_in) {}

    @PostMapping(value="/api/v1/auth/service-token",consumes=MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Token> token(@RequestHeader(value="Authorization",defaultValue="") String authorization,
            @RequestParam String grant_type) {
        if (!"client_credentials".equals(grant_type)) throw new BadCredentialsException("Invalid grant type");
        byte[] supplied;
        try {
            if (!authorization.startsWith("Basic ")) throw new IllegalArgumentException();
            supplied = Base64.getDecoder().decode(authorization.substring(6));
        } catch (IllegalArgumentException ex) { throw new BadCredentialsException("Invalid service credentials"); }

        if (dispatchSecret.length() >= 32) {
            byte[] expected = ("dispatch-service:" + dispatchSecret).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, supplied)) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(new Token(tokens.createServiceToken(DISPATCH_SUBJECT, "dispatch.internal"), "Bearer", 300));
            }
        }
        if (merchantSecret.length() >= 32) {
            byte[] expected = ("merchant-service:" + merchantSecret).getBytes(StandardCharsets.UTF_8);
            if (MessageDigest.isEqual(expected, supplied)) {
                return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                        .body(new Token(tokens.createServiceToken(MERCHANT_SUBJECT, "merchant.internal"), "Bearer", 300));
            }
        }
        throw new BadCredentialsException("Invalid service credentials");
    }
}

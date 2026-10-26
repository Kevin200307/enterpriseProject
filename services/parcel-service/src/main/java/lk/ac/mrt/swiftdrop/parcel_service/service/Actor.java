package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public record Actor(UUID id, Set<String> roles, String bearerToken, UUID merchantId) {
    public Actor(UUID id, Set<String> roles, String token) { this(id, roles, token, null); }
    public static Actor from(Authentication authentication) {
        UUID id;
        try { id = UUID.fromString(authentication.getName()); }
        catch (IllegalArgumentException | NullPointerException ex) {
            throw new ParcelException(ErrorCode.ACCESS_DENIED, "A UUID user subject is required");
        }
        Set<String> roles = new HashSet<>();
        authentication.getAuthorities().forEach(a -> roles.add(a.getAuthority()));
        String token = authentication instanceof JwtAuthenticationToken jwt ? jwt.getToken().getTokenValue() : null;
        UUID merchantId = null;
        if (authentication instanceof JwtAuthenticationToken jwt && jwt.getToken().hasClaim("merchant_id")) {
            try { merchantId = UUID.fromString(jwt.getToken().getClaimAsString("merchant_id")); }
            catch (IllegalArgumentException | NullPointerException ex) {
                throw new ParcelException(ErrorCode.ACCESS_DENIED, "A UUID merchant_id claim is required");
            }
        }
        return new Actor(id, Set.copyOf(roles), token, merchantId);
    }
    public boolean has(String role) { return roles.contains("ROLE_" + role); }
    public boolean manages() { return has("ADMIN") || has("DISPATCHER"); }
}

package lk.ac.mrt.swiftdrop.merchant_service.security;

import java.util.List;
import lk.ac.mrt.swiftdrop.merchant_service.domain.ApiKey;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Authentication token populated by {@link ApiKeyAuthFilter} after a verified SDK key. */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final ApiKey apiKey;

    public ApiKeyAuthentication(ApiKey apiKey) {
        super(List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")));
        this.apiKey = apiKey;
        setAuthenticated(true);
    }

    /** The owner user ID from the key's associated merchant — acts as the principal. */
    @Override
    public Object getPrincipal() {
        return apiKey.getMerchant().getOwnerUserId();
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }
}

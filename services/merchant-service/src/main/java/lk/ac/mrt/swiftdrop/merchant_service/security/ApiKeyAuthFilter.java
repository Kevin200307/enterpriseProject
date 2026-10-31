package lk.ac.mrt.swiftdrop.merchant_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lk.ac.mrt.swiftdrop.merchant_service.domain.ApiKey;
import lk.ac.mrt.swiftdrop.merchant_service.service.ApiKeyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Intercepts requests carrying an SDK API key ({@code X-Api-Key} header or
 * {@code Authorization: Bearer sdk_…} header) and installs an
 * {@link ApiKeyAuthentication} in the security context.
 *
 * <p>JWT bearer tokens (no {@code sdk_} prefix) are left untouched for the
 * standard OAuth2 resource-server filter to process.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final String SDK_PREFIX = "sdk_";
    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = extractKey(request);
        if (key == null || !key.startsWith(SDK_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

        String scope = requiredScope(request);
        try {
            ApiKey verified = apiKeyService.verify(key, scope);
            SecurityContextHolder.getContext()
                    .setAuthentication(new ApiKeyAuthentication(verified));
        } catch (SecurityException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"errorCode\":\"UNAUTHENTICATED\",\"detail\":\"Invalid or expired API key\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private static String extractKey(HttpServletRequest req) {
        // Prefer the dedicated header; fall back to Authorization: Bearer sdk_…
        String header = req.getHeader("X-Api-Key");
        if (header != null && !header.isBlank()) return header.trim();
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (token.startsWith(SDK_PREFIX)) return token;
        }
        return null;
    }

    /** Parcels:write for mutating requests, parcels:read otherwise. */
    private static String requiredScope(HttpServletRequest req) {
        String method = req.getMethod();
        return ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method))
                ? "parcels:write"
                : "parcels:read";
    }
}

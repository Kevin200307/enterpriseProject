package lk.ac.mrt.swiftdrop.identity_service.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Utility functions for extracting the authenticated user's identity from the SecurityContext.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * Extracts the current authenticated user's UUID from the SecurityContext.
     *
     * @return the authenticated user's UUID
     * @throws InsufficientAuthenticationException if unauthenticated or principal is invalid
     */
    public static UUID getAuthenticatedUserId() {
        return extractUserId(SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Extracts the user's UUID from an Authentication instance (or SecurityContext if null).
     *
     * @param authentication the current Authentication
     * @return the authenticated user's UUID
     * @throws InsufficientAuthenticationException if unauthenticated, anonymous, or principal is not a valid UUID
     */
    public static UUID extractUserId(Authentication authentication) {
        Authentication auth = (authentication != null)
                ? authentication
                : SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new InsufficientAuthenticationException("Full authentication is required to access this resource");
        }

        String principalName = auth.getName();
        if (principalName == null || principalName.isBlank() || "anonymousUser".equalsIgnoreCase(principalName)) {
            throw new InsufficientAuthenticationException("Full authentication is required to access this resource");
        }

        try {
            return UUID.fromString(principalName);
        } catch (IllegalArgumentException ex) {
            throw new InsufficientAuthenticationException("Invalid user identifier in authentication context: " + principalName);
        }
    }
}

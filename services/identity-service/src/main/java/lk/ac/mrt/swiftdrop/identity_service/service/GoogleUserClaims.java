package lk.ac.mrt.swiftdrop.identity_service.service;

import java.util.Objects;

/**
 * Verified user identity claims extracted from a cryptographically valid Google OIDC ID token.
 */
public record GoogleUserClaims(
        String sub,
        String email,
        boolean emailVerified,
        String name,
        String givenName,
        String familyName,
        String picture
) {
    public GoogleUserClaims {
        Objects.requireNonNull(sub, "Google sub must not be null");
        Objects.requireNonNull(email, "Google email must not be null");
        if (sub.isBlank()) {
            throw new IllegalArgumentException("Google sub must not be blank");
        }
        if (email.isBlank()) {
            throw new IllegalArgumentException("Google email must not be blank");
        }
    }
}

package lk.ac.mrt.swiftdrop.contracts.error;

import java.net.URI;
import java.util.Locale;

/**
 * Stable, machine-readable error codes returned by every service in the RFC 7807
 * {@code errorCode} extension field. Clients branch on the code, never on the title or detail text.
 */
public enum ErrorCode {
    VALIDATION_FAILED(400, "Validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),
    UNAUTHENTICATED(401, "Authentication required"),
    INVALID_CREDENTIALS(401, "Invalid credentials"),
    INVALID_API_KEY(401, "Invalid API key"),
    ACCESS_DENIED(403, "Access denied"),
    MERCHANT_NOT_APPROVED(403, "Merchant not approved"),
    RESOURCE_NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Conflict"),
    INVALID_STATUS_TRANSITION(409, "Invalid parcel status transition"),
    RATE_LIMITED(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal server error"),
    UPSTREAM_UNAVAILABLE(503, "Upstream service unavailable");

    private static final String TYPE_PREFIX = "urn:swiftdrop:error:";

    private final int httpStatus;
    private final String title;
    private final URI type;

    ErrorCode(int httpStatus, String title) {
        this.httpStatus = httpStatus;
        this.title = title;
        this.type = URI.create(TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String title() {
        return title;
    }

    /** RFC 7807 {@code type} member, e.g. {@code urn:swiftdrop:error:validation-failed}. */
    public URI type() {
        return type;
    }
}

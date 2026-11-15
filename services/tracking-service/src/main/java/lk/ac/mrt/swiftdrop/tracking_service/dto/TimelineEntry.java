package lk.ac.mrt.swiftdrop.tracking_service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Objects;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;

/**
 * Timeline entry matching frontend {@code { status, occurredAt, note? }}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TimelineEntry(
        ParcelStatus status,
        Instant occurredAt,
        String note) {

    public TimelineEntry {
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }
}

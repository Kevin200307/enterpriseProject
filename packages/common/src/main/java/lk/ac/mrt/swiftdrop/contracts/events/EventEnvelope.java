package lk.ac.mrt.swiftdrop.contracts.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Wrapper for every message published to RabbitMQ. Consumers must use {@code eventId}
 * to discard duplicates, since the broker delivers at-least-once.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        int version,
        Instant occurredAt,
        String producer,
        T payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(producer, "producer");
        Objects.requireNonNull(payload, "payload");
        if (version < 1) {
            throw new IllegalArgumentException("version must be >= 1, was " + version);
        }
    }

    public static <T> EventEnvelope<T> of(String eventType, int version, String producer, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, version, Instant.now(), producer, payload);
    }
}

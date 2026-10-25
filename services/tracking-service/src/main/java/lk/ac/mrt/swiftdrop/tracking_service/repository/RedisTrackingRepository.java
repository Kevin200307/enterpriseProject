package lk.ac.mrt.swiftdrop.tracking_service.repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lk.ac.mrt.swiftdrop.tracking_service.config.TrackingProperties;
import lk.ac.mrt.swiftdrop.tracking_service.domain.TrackingProjection;
import lk.ac.mrt.swiftdrop.tracking_service.dto.TimelineEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisTrackingRepository {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TrackingProperties properties;

    private static final String PARCEL_PREFIX = "tracking:parcel:";
    private static final String TIMELINE_PREFIX = "tracking:timeline:";
    private static final int MAX_TIMELINE_ENTRIES = 50;

    public Duration getProjectionTtl() {
        int days = properties.cache() != null ? properties.cache().parcelTtlDays() : 90;
        return Duration.ofDays(days);
    }

    public Optional<TrackingProjection> findByTrackingNumber(String trackingNumber) {
        try {
            String parcelJson = redisTemplate.opsForValue().get(PARCEL_PREFIX + trackingNumber).block();
            if (parcelJson == null || parcelJson.isBlank()) {
                return Optional.empty();
            }

            TrackingProjection current = objectMapper.readValue(parcelJson, TrackingProjection.class);

            // Fetch timeline
            List<String> rawTimeline = redisTemplate.opsForList().range(TIMELINE_PREFIX + trackingNumber, 0, -1)
                    .collectList().block();

            List<TimelineEntry> timeline = new ArrayList<>();
            if (rawTimeline != null && !rawTimeline.isEmpty()) {
                for (String entryJson : rawTimeline) {
                    try {
                        timeline.add(objectMapper.readValue(entryJson, TimelineEntry.class));
                    } catch (Exception e) {
                        log.warn("Failed to parse timeline entry for {}: {}", trackingNumber, entryJson);
                    }
                }
                timeline.sort(Comparator.comparing(TimelineEntry::occurredAt));
            } else if (current.timeline() != null) {
                timeline.addAll(current.timeline());
            }

            return Optional.of(new TrackingProjection(
                    current.trackingNumber(),
                    current.status(),
                    current.originCity(),
                    current.destinationCity(),
                    current.courierFirstName(),
                    current.eta(),
                    current.updatedAt(),
                    timeline
            ));
        } catch (Exception ex) {
            log.error("Error reading projection for {} from Redis: {}", trackingNumber, ex.getMessage());
            throw new RedisStorageException("Failed to read from Redis", ex);
        }
    }

    public void saveProjection(TrackingProjection projection) {
        try {
            Duration ttl = getProjectionTtl();
            String tn = projection.trackingNumber();

            // Save current parcel state
            String json = objectMapper.writeValueAsString(projection);
            redisTemplate.opsForValue().set(PARCEL_PREFIX + tn, json, ttl).block();

            // Store timeline entries
            String timelineKey = TIMELINE_PREFIX + tn;
            if (projection.timeline() != null && !projection.timeline().isEmpty()) {
                redisTemplate.delete(timelineKey).block();
                List<TimelineEntry> sorted = new ArrayList<>(projection.timeline());
                sorted.sort(Comparator.comparing(TimelineEntry::occurredAt));

                // Cap timeline entries
                if (sorted.size() > MAX_TIMELINE_ENTRIES) {
                    sorted = sorted.subList(sorted.size() - MAX_TIMELINE_ENTRIES, sorted.size());
                }

                for (TimelineEntry entry : sorted) {
                    redisTemplate.opsForList().rightPush(timelineKey, objectMapper.writeValueAsString(entry)).block();
                }
                redisTemplate.expire(timelineKey, ttl).block();
            }
        } catch (Exception ex) {
            log.error("Error writing projection for {} to Redis: {}", projection.trackingNumber(), ex.getMessage());
            throw new RedisStorageException("Failed to write to Redis", ex);
        }
    }

    public static class RedisStorageException extends RuntimeException {
        public RedisStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

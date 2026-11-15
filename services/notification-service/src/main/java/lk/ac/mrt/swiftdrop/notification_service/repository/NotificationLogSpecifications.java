package lk.ac.mrt.swiftdrop.notification_service.repository;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dynamic JPA specifications for querying notification delivery logs.
 */
public final class NotificationLogSpecifications {

    private NotificationLogSpecifications() {}

    /**
     * Builds a specification combining optional filters: status, channel, and date range [from, to].
     */
    public static Specification<NotificationLog> withFilters(
            NotificationStatus status,
            NotificationChannel channel,
            Instant from,
            Instant to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (channel != null) {
                predicates.add(cb.equal(root.get("channel"), channel));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

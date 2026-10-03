// retrieve data from database and check idempotency

package lk.ac.mrt.swiftdrop.notification_service.repository;

import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Idempotency check
     */
    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    /**
     * find by tracking number
     */
    List<NotificationLog> findByTrackingNumberOrderByCreatedAtDesc(String trackingNumber);
}
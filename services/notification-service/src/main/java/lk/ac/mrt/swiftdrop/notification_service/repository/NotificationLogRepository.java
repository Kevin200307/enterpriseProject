package lk.ac.mrt.swiftdrop.notification_service.repository;

import java.util.List;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationChannel;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationLog;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID>, JpaSpecificationExecutor<NotificationLog> {

    /**
     * Idempotency Check: (event_id, channel, recipient_ref) අනුව
     * දැනටමත් notification එකක් යවා ඇත්දැයි පරීක්ෂා කිරීමට.
     */
    boolean existsByEventIdAndChannelAndRecipientRef(UUID eventId, NotificationChannel channel, UUID recipientRef);

    boolean existsByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    /**
     * Tracking Number එක අනුව notifications ලබාගැනීමට.
     */
    List<NotificationLog> findByTrackingNumberOrderByCreatedAtDesc(String trackingNumber);

    /**
     * User ID (recipient_ref) එක අනුව notifications ලබාගැනීමට.
     */
    List<NotificationLog> findByRecipientRefOrderByCreatedAtDesc(UUID recipientRef);

    /**
     * Scheduled Retry: Fail වූ (status = FAILED) සහ උපරිම attempts (උදා: 5) ට වඩා අඩු notifications ලබාගැනීමට.
     */
    List<NotificationLog> findByStatusAndAttemptsLessThan(NotificationStatus status, int maxAttempts);
}
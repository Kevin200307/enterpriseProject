package lk.ac.mrt.swiftdrop.notification_service.repository;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.notification_service.domain.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
}

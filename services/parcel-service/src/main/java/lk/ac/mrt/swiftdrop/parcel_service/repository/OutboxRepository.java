package lk.ac.mrt.swiftdrop.parcel_service.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import lk.ac.mrt.swiftdrop.parcel_service.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.createdAt, e.id")
    List<OutboxEvent> pending(Pageable pageable);
}

package lk.ac.mrt.swiftdrop.parcel_service.repository;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.parcel_service.domain.StatusHistory;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HistoryRepository extends JpaRepository<StatusHistory, UUID> {
    Page<StatusHistory> findByParcelId(UUID parcelId, Pageable pageable);
    java.util.List<StatusHistory> findByParcelIdOrderByOccurredAtAsc(UUID parcelId);
}

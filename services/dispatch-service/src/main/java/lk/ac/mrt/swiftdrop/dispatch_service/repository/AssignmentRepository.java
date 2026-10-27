package lk.ac.mrt.swiftdrop.dispatch_service.repository;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.AssignmentEntity;
import org.springframework.data.jpa.repository.*;

public interface AssignmentRepository extends JpaRepository<AssignmentEntity,UUID> {
    Optional<AssignmentEntity> findByActiveParcelId(UUID parcelId);
    @Query("select p.trackingNumber from AssignmentEntity a join DispatchParcel p on p.id=a.parcelId where a.courierId=:courierId and a.closedAt is null order by p.trackingNumber")
    List<String> openTrackingNumbers(UUID courierId);
}

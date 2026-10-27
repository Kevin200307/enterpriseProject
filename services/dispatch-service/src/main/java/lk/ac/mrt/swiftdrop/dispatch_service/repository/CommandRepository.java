package lk.ac.mrt.swiftdrop.dispatch_service.repository;

import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.AssignmentCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface CommandRepository extends JpaRepository<AssignmentCommand,UUID> {
    Optional<AssignmentCommand> findByPendingParcelId(UUID parcelId);
    List<AssignmentCommand> findByStateOrderByCreatedAtAsc(String state, Pageable page);
    @Query("select p.trackingNumber from AssignmentCommand c join DispatchParcel p on p.id=c.parcelId where c.courierId=:id and c.state='PENDING' order by p.trackingNumber")
    List<String> pendingTrackingNumbers(UUID id);
}

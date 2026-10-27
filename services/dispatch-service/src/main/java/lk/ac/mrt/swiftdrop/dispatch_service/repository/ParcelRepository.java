package lk.ac.mrt.swiftdrop.dispatch_service.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.DispatchParcel;
import org.springframework.data.jpa.repository.*;

public interface ParcelRepository extends JpaRepository<DispatchParcel,UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from DispatchParcel p where p.id = :id")
    Optional<DispatchParcel> lock(UUID id);
}

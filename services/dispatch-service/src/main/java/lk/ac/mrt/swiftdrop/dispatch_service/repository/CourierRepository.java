package lk.ac.mrt.swiftdrop.dispatch_service.repository;

import jakarta.persistence.LockModeType;
import java.util.*;
import lk.ac.mrt.swiftdrop.dispatch_service.domain.CourierEntity;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface CourierRepository extends JpaRepository<CourierEntity,UUID>, JpaSpecificationExecutor<CourierEntity> {
    Optional<CourierEntity> findByUserId(UUID userId);
    boolean existsByUserId(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from CourierEntity c where c.id in :ids order by c.id")
    List<CourierEntity> lockAll(@Param("ids") Collection<UUID> ids);
}

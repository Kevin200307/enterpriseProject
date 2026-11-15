package lk.ac.mrt.swiftdrop.pricing_service.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.pricing_service.domain.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneRepository extends JpaRepository<Zone, UUID> {
    List<Zone> findByActiveTrueOrderByNameAsc();
    List<Zone> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByCodeIgnoreCase(String code);

    @Query("select distinct z from Zone z join z.cities c where c.normalizedCity = :city and z.active = true")
    Optional<Zone> findActiveByNormalizedCity(@Param("city") String city);

    @Query("select distinct z from Zone z join z.cities c where c.normalizedCity = :city")
    Optional<Zone> findByNormalizedCity(@Param("city") String city);
}
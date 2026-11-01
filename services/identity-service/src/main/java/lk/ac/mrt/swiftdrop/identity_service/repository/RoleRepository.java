package lk.ac.mrt.swiftdrop.identity_service.repository;

import lk.ac.mrt.swiftdrop.contracts.domain.Role;
import lk.ac.mrt.swiftdrop.identity_service.domain.RoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for system roles.
 */
@Repository
public interface RoleRepository extends JpaRepository<RoleEntity, String> {

    default Optional<RoleEntity> findByRole(Role role) {
        return findById(role.name());
    }
}

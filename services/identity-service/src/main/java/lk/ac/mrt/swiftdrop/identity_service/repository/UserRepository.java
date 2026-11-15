package lk.ac.mrt.swiftdrop.identity_service.repository;

import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import lk.ac.mrt.swiftdrop.identity_service.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the User aggregate root.
 * Supports case-insensitive email lookups, OAuth subject binding, and dynamic query specifications.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /**
     * Case-insensitive email lookup for authentication and profile retrieval.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Checks if an email is already registered, case-insensitively.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Finds a user by their Google OAuth subject identifier (sub claim).
     */
    Optional<User> findByGoogleSub(String googleSub);

    /**
     * Checks if a Google subject identifier is already linked to an account.
     */
    boolean existsByGoogleSub(String googleSub);

    /**
     * Batch lookup for inter-service communication (e.g. dispatch and notification services).
     */
    List<User> findAllByIdIn(Collection<UUID> ids);

    /**
     * Paginated lookup filtered by user status.
     */
    Page<User> findByStatus(UserStatus status, Pageable pageable);

    /**
     * Paginated lookup filtered by role identifier.
     */
    Page<User> findByRoles_Id(String roleId, Pageable pageable);

    /**
     * Paginated lookup filtered by both role and status.
     */
    Page<User> findByRoles_IdAndStatus(String roleId, UserStatus status, Pageable pageable);
}

package lk.ac.mrt.swiftdrop.identity_service.repository;

import lk.ac.mrt.swiftdrop.identity_service.domain.RefreshToken;
import lk.ac.mrt.swiftdrop.identity_service.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for the RefreshToken entity.
 * Provides high-speed lookups by SHA-256 token hash and bulk revocation for replay mitigation.
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Finds a refresh token by its unique SHA-256 hash.
     *
     * @param tokenHash 64-character lowercase hexadecimal hash
     * @return Optional containing the matching RefreshToken if found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Finds all refresh tokens belonging to a given user.
     *
     * @param user the owning User
     * @return list of refresh tokens
     */
    List<RefreshToken> findByUser(User user);

    /**
     * Finds all refresh tokens for a given user ID.
     *
     * @param userId UUID of the user
     * @return list of refresh tokens
     */
    List<RefreshToken> findByUserId(UUID userId);

    /**
     * Finds all unrevoked refresh tokens for a given user ID.
     *
     * @param userId UUID of the user
     * @return list of active/unrevoked refresh tokens
     */
    List<RefreshToken> findByUserIdAndRevokedAtIsNull(UUID userId);

    /**
     * Revokes all active refresh tokens for the given user (e.g. on logout-all or replay detection).
     *
     * @param userId user UUID whose tokens should be revoked
     * @param revokedAt revocation timestamp
     * @return count of tokens updated
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllForUserId(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);

    /**
     * Purges expired refresh tokens older than the specified instant.
     *
     * @param now current threshold timestamp
     * @return count of deleted tokens
     */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") Instant now);
}

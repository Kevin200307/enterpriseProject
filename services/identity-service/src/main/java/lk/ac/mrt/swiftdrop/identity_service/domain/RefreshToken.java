package lk.ac.mrt.swiftdrop.identity_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity representing an opaque refresh token stored as a SHA-256 hash.
 * Supports token rotation and tracks chain replacement for replay detection (§4.3).
 */
@Entity
@Table(name = "refresh_tokens")
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by")
    private UUID replacedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Default constructor for JPA.
     */
    public RefreshToken() {
    }

    /**
     * Convenience constructor initializing an active refresh token.
     *
     * @param user the owning user
     * @param tokenHash the 64-character lowercase SHA-256 hex hash of the raw token
     * @param expiresAt token expiration timestamp
     */
    public RefreshToken(User user, String tokenHash, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "User must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "Token hash must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "ExpiresAt must not be null");
        this.createdAt = Instant.now();
    }

    /**
     * Checks if this token has expired based on current instant.
     */
    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    /**
     * Checks if this token was explicitly revoked or rotated.
     */
    public boolean isRevoked() {
        return this.revokedAt != null;
    }

    /**
     * Checks if this token is currently active (neither expired nor revoked).
     */
    public boolean isActive() {
        return !isExpired() && !isRevoked();
    }

    /**
     * Revokes this refresh token immediately.
     */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = Instant.now();
        }
    }

    /**
     * Rotates this token, marking it revoked and recording the replacement token ID
     * for replay detection.
     *
     * @param newRefreshTokenId the UUID of the newly issued replacement token
     */
    public void rotate(UUID newRefreshTokenId) {
        revoke();
        this.replacedBy = newRefreshTokenId;
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public UUID getReplacedBy() {
        return replacedBy;
    }

    public void setReplacedBy(UUID replacedBy) {
        this.replacedBy = replacedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken that)) return false;
        if (id != null && that.id != null) {
            return Objects.equals(id, that.id);
        }
        return Objects.equals(tokenHash, that.tokenHash);
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(tokenHash);
    }

    @Override
    public String toString() {
        String maskedHash = (tokenHash != null && tokenHash.length() >= 8)
                ? tokenHash.substring(0, 8) + "..."
                : "null";
        return "RefreshToken{" +
                "id=" + id +
                ", tokenHash='" + maskedHash + '\'' +
                ", expiresAt=" + expiresAt +
                ", revokedAt=" + revokedAt +
                ", replacedBy=" + replacedBy +
                ", createdAt=" + createdAt +
                '}';
    }
}

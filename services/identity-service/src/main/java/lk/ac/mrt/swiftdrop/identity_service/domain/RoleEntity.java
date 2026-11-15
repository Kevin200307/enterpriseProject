package lk.ac.mrt.swiftdrop.identity_service.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.ac.mrt.swiftdrop.contracts.domain.Role;

import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity representing a system role in the 'roles' table.
 * Maps directly to values in the shared {@link Role} enum.
 */
@Entity
@Table(name = "roles")
public class RoleEntity {

    @Id
    @Column(length = 50, nullable = false)
    private String id;

    @Column(length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public RoleEntity() {
    }

    public RoleEntity(Role role, String description) {
        this.id = role.name();
        this.description = description;
        this.createdAt = Instant.now();
    }

    public RoleEntity(String id, String description) {
        this.id = id;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Role toRole() {
        return Role.valueOf(this.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoleEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "RoleEntity{" +
                "id='" + id + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}

package lk.ac.mrt.swiftdrop.pricing_service.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "zones")
public class Zone {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120, unique = true)
    private String name;

    @Column(nullable = false, length = 12, unique = true)
    private String code;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "zone_cities", joinColumns = @JoinColumn(name = "zone_id"))
    private Set<ZoneCity> cities = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Zone() {}

    public Zone(String name, String code, Set<ZoneCity> cities, boolean active) {
        this.name = name;
        this.code = code;
        this.cities = new LinkedHashSet<>(cities);
        this.active = active;
    }

    @PrePersist void onCreate() { createdAt = updatedAt = Instant.now(); }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCode() { return code; }
    public Set<ZoneCity> getCities() { return Set.copyOf(cities); }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String name, String code, Set<ZoneCity> cities, boolean active) {
        this.name = name;
        this.code = code;
        this.cities.clear();
        this.cities.addAll(cities);
        this.active = active;
    }
    public void deactivate() { active = false; }
}
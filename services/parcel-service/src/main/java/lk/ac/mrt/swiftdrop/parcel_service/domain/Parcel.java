package lk.ac.mrt.swiftdrop.parcel_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.*;

@Entity
@Table(name = "parcels")
public class Parcel {
    @Id private UUID id;
    @Version private long version;
    @Column(nullable = false, unique = true) private String trackingNumber;
    @Column(nullable = false) private UUID customerId;
    private UUID assigneeId;
    private UUID merchantId;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="contactName", column=@Column(name="pickup_contact_name")),
        @AttributeOverride(name="phone", column=@Column(name="pickup_phone")),
        @AttributeOverride(name="line1", column=@Column(name="pickup_line1")),
        @AttributeOverride(name="city", column=@Column(name="pickup_city")),
        @AttributeOverride(name="postalCode", column=@Column(name="pickup_postal_code")),
        @AttributeOverride(name="zone", column=@Column(name="pickup_zone"))})
    private Address pickup;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name="contactName", column=@Column(name="dropoff_contact_name")),
        @AttributeOverride(name="phone", column=@Column(name="dropoff_phone")),
        @AttributeOverride(name="line1", column=@Column(name="dropoff_line1")),
        @AttributeOverride(name="city", column=@Column(name="dropoff_city")),
        @AttributeOverride(name="postalCode", column=@Column(name="dropoff_postal_code")),
        @AttributeOverride(name="zone", column=@Column(name="dropoff_zone"))})
    private Address dropoff;
    @Enumerated(EnumType.STRING) private PackageSize size;
    @Column(precision=10, scale=3) private BigDecimal weightKg;
    @Column(precision=12, scale=2) private BigDecimal fee;
    private String currency;
    @Enumerated(EnumType.STRING) private ParcelStatus status;
    private String bookingKey;
    private String requestHash;
    private Instant createdAt;
    private Instant updatedAt;

    public Parcel(UUID customerId, UUID merchantId, String trackingNumber, Address pickup, Address dropoff,
                  PackageSize size, BigDecimal weightKg, BigDecimal fee, String currency,
                  String bookingKey, String requestHash, Instant now) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.merchantId = merchantId;
        this.trackingNumber = trackingNumber;
        this.pickup = pickup;
        this.dropoff = dropoff;
        this.size = size;
        this.weightKg = weightKg;
        this.fee = fee;
        this.currency = currency;
        this.status = ParcelStatus.CREATED;
        this.bookingKey = bookingKey;
        this.requestHash = requestHash;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void assign(UUID courierId, Instant now) {
        this.assigneeId = courierId;
        this.updatedAt = now;
    }

    public void changeStatus(ParcelStatus next, Instant now) {
        this.status = next;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }

    public long getVersion() { return version; }

    public String getTrackingNumber() { return trackingNumber; }

    public UUID getCustomerId() { return customerId; }

    public UUID getAssigneeId() { return assigneeId; }

    public UUID getMerchantId() { return merchantId; }

    public Address getPickup() { return pickup; }

    public Address getDropoff() { return dropoff; }

    public PackageSize getSize() { return size; }

    public BigDecimal getWeightKg() { return weightKg; }

    public BigDecimal getFee() { return fee; }

    public String getCurrency() { return currency; }

    public ParcelStatus getStatus() { return status; }

    public String getBookingKey() { return bookingKey; }

    public String getRequestHash() { return requestHash; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }

    protected Parcel() {}
}

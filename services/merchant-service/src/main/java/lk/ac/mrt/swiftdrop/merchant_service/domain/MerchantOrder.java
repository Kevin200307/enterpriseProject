package lk.ac.mrt.swiftdrop.merchant_service.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity @Table(name="merchant_orders", uniqueConstraints=@UniqueConstraint(columnNames={"merchant_id", "merchant_ref"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class MerchantOrder {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="merchant_id", nullable=false) private Merchant merchant;
    @Column(name="merchant_ref", nullable=false, length=100) private String merchantRef;
    @Column(name="parcel_id", nullable=false, unique=true) private UUID parcelId;
    @Column(name="tracking_number", nullable=false) private String trackingNumber;
    @Column(nullable=false) private String status;
    @Column(name="destination_city", nullable=false) private String destinationCity;
    @Column(nullable=false, precision=12, scale=2) private BigDecimal fee;
    @Column(nullable=false, length=3) private String currency;
    @Column(name="request_hash", nullable=false, length=64) private String requestHash;
    @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private Instant createdAt;
    @UpdateTimestamp @Column(name="updated_at", nullable=false) private Instant updatedAt;
}

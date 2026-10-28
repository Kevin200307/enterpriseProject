package lk.ac.mrt.swiftdrop.merchant_service.repository;

import lk.ac.mrt.swiftdrop.merchant_service.domain.WebhookDelivery;
import lk.ac.mrt.swiftdrop.merchant_service.domain.WebhookDeliveryState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {

    Optional<WebhookDelivery> findByEventIdAndMerchantId(UUID eventId, UUID merchantId);

    List<WebhookDelivery> findByStateAndNextRetryAtBefore(WebhookDeliveryState state, Instant retryBefore);

    List<WebhookDelivery> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
    Page<WebhookDelivery> findByMerchantId(UUID merchantId, Pageable pageable);
    Optional<WebhookDelivery> findByIdAndMerchantId(UUID id, UUID merchantId);
}

package lk.ac.mrt.swiftdrop.merchant_service.repository;

import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MerchantOrderRepository extends JpaRepository<MerchantOrder, UUID> {
    Optional<MerchantOrder> findByMerchantIdAndMerchantRef(UUID merchantId, String merchantRef);
    Optional<MerchantOrder> findByIdAndMerchantId(UUID id, UUID merchantId);
    Page<MerchantOrder> findByMerchantId(UUID merchantId, Pageable pageable);
    Page<MerchantOrder> findByMerchantIdAndMerchantRefContainingIgnoreCase(UUID merchantId, String query, Pageable pageable);
}

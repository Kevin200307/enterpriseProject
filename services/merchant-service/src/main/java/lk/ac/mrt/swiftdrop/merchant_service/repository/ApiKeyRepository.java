package lk.ac.mrt.swiftdrop.merchant_service.repository;

import lk.ac.mrt.swiftdrop.merchant_service.domain.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findAllByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

    List<ApiKey> findAllByKeyPrefixAndRevokedAtIsNull(String keyPrefix);

    java.util.Optional<ApiKey> findByIdAndMerchantId(UUID id, UUID merchantId);
}

package lk.ac.mrt.swiftdrop.merchant_service.repository;

import lk.ac.mrt.swiftdrop.merchant_service.domain.OAuthClient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuthClientRepository extends JpaRepository<OAuthClient, UUID> {

    Optional<OAuthClient> findByClientId(String clientId);

    Optional<OAuthClient> findByMerchantId(UUID merchantId);
}

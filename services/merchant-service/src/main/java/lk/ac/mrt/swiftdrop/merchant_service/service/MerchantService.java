package lk.ac.mrt.swiftdrop.merchant_service.service;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.merchant_service.api.MerchantRegistrationRequest;
import lk.ac.mrt.swiftdrop.merchant_service.api.MerchantResponse;
import lk.ac.mrt.swiftdrop.merchant_service.domain.Merchant;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantRepository;
import lk.ac.mrt.swiftdrop.merchant_service.service.exception.MerchantAlreadyExistsException;
import lk.ac.mrt.swiftdrop.merchant_service.service.exception.IdentityServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final WebClient.Builder webClientBuilder;
    private final String identityServiceUrl;
    private final String identityServiceToken;

    public MerchantService(MerchantRepository merchantRepository, WebClient.Builder webClientBuilder) {
        this.merchantRepository = merchantRepository;
        this.webClientBuilder = webClientBuilder;
        this.identityServiceUrl = System.getenv().getOrDefault("IDENTITY_SERVICE_URL", "http://identity-service:8080");
        this.identityServiceToken = System.getenv().getOrDefault("IDENTITY_SERVICE_TOKEN", "");
    }

    @Transactional
    public MerchantResponse registerMerchant(UUID ownerUserId, MerchantRegistrationRequest request) {
        log.info("Registering merchant for user: {}", ownerUserId);

        if (merchantRepository.existsByOwnerUserId(ownerUserId)) {
            throw new MerchantAlreadyExistsException("User already has a merchant account");
        }

        Merchant merchant = Merchant.builder()
                .ownerUserId(ownerUserId)
                .orgName(request.orgName())
                .businessRegNo(request.businessRegNo())
                .contactEmail(request.contactEmail())
                .contactPhone(request.contactPhone())
                .status(MerchantStatus.PENDING)
                .build();

        merchant = merchantRepository.save(merchant);
        log.info("Merchant registered successfully with id: {}", merchant.getId());

        return toResponse(merchant);
    }

    @Transactional(readOnly = true)
    public Page<MerchantResponse> listMerchants(MerchantStatus status, Pageable pageable) {
        log.info("Listing merchants with status: {}", status);
        if (status != null) {
            return merchantRepository.findByStatus(status, pageable)
                    .map(this::toResponse);
        }
        return merchantRepository.findAll(pageable)
                .map(this::toResponse);
    }

    @Transactional
    public MerchantResponse approveMerchant(UUID merchantId, UUID approvedBy) {
        log.info("Approving merchant: {} by admin: {}", merchantId, approvedBy);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        if (merchant.getStatus() == MerchantStatus.APPROVED) {
            grantMerchantRole(merchant.getOwnerUserId(), merchant.getId());
            return toResponse(merchant);
        }
        if (merchant.getStatus() != MerchantStatus.PENDING) {
            throw new IllegalStateException("Can only approve pending merchants");
        }

        merchant.setStatus(MerchantStatus.APPROVED);
        merchant.setApprovedBy(approvedBy);
        merchant.setApprovedAt(Instant.now());

        merchant = merchantRepository.save(merchant);

        // Call identity-service to grant MERCHANT role
        grantMerchantRole(merchant.getOwnerUserId(), merchant.getId());

        log.info("Merchant approved successfully: {}", merchantId);
        return toResponse(merchant);
    }

    @Transactional
    public MerchantResponse rejectMerchant(UUID merchantId, UUID rejectedBy) {
        log.info("Rejecting merchant: {} by admin: {}", merchantId, rejectedBy);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        if (merchant.getStatus() != MerchantStatus.PENDING) {
            throw new IllegalStateException("Can only reject pending merchants");
        }

        merchant.setStatus(MerchantStatus.REJECTED);
        merchant.setApprovedBy(rejectedBy);
        merchant.setApprovedAt(Instant.now());

        merchant = merchantRepository.save(merchant);
        log.info("Merchant rejected successfully: {}", merchantId);

        return toResponse(merchant);
    }

    @Transactional
    public MerchantResponse suspendMerchant(UUID merchantId, UUID suspendedBy) {
        log.info("Suspending merchant: {} by admin: {}", merchantId, suspendedBy);

        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));

        if (merchant.getStatus() != MerchantStatus.APPROVED) {
            throw new IllegalStateException("Can only suspend approved merchants");
        }

        merchant.setStatus(MerchantStatus.SUSPENDED);
        merchant.setApprovedBy(suspendedBy);
        merchant.setApprovedAt(Instant.now());

        merchant = merchantRepository.save(merchant);
        log.info("Merchant suspended successfully: {}", merchantId);

        return toResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchant(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found"));
        return toResponse(merchant);
    }

    @Transactional(readOnly = true)
    public MerchantResponse getMerchantByOwnerUserId(UUID ownerUserId) {
        Merchant merchant = merchantRepository.findByOwnerUserId(ownerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Merchant not found for user"));
        return toResponse(merchant);
    }

    private void grantMerchantRole(UUID userId, UUID merchantId) {
        try {
            WebClient webClient = webClientBuilder.build();

            String endpoint = String.format("%s/api/v1/internal/users/%s/merchant-role", identityServiceUrl, userId);
            if (identityServiceToken.isBlank()) throw new IdentityServiceException("IDENTITY_SERVICE_TOKEN is required");
            Exception last = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    webClient.post().uri(endpoint).headers(h -> h.setBearerAuth(identityServiceToken))
                            .retrieve().bodyToMono(Void.class).block();
                    last = null;
                    break;
                } catch (Exception ex) {
                    last = ex;
                    if (attempt < 3) try { Thread.sleep(50L * attempt); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new IdentityServiceException("Role grant interrupted", interrupted);
                    }
                }
            }
            if (last != null) throw last;

            log.info("Successfully granted MERCHANT role to user: {} with merchantId: {}", userId, merchantId);
        } catch (WebClientResponseException e) {
            log.error("Failed to grant MERCHANT role to user: {}. Status: {}, Body: {}",
                    userId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IdentityServiceException("Failed to grant MERCHANT role via identity-service", e);
        } catch (Exception e) {
            log.error("Unexpected error calling identity-service for user: {}", userId, e);
            throw new IdentityServiceException("Unexpected error calling identity-service", e);
        }
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return new MerchantResponse(
                merchant.getId(),
                merchant.getOwnerUserId(),
                merchant.getOrgName(),
                merchant.getBusinessRegNo(),
                merchant.getContactEmail(),
                merchant.getContactPhone(),
                merchant.getStatus(),
                merchant.getApprovedBy(),
                merchant.getApprovedAt(),
                merchant.getCreatedAt(),
                merchant.getUpdatedAt()
        );
    }
}

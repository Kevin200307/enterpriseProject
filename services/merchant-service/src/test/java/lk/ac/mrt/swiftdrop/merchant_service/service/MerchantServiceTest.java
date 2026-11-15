package lk.ac.mrt.swiftdrop.merchant_service.service;

import lk.ac.mrt.swiftdrop.merchant_service.api.MerchantRegistrationRequest;
import lk.ac.mrt.swiftdrop.merchant_service.domain.Merchant;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantRepository;
import lk.ac.mrt.swiftdrop.merchant_service.service.exception.MerchantAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MerchantServiceTest {

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    private MerchantService merchantService;

    @BeforeEach
    void setUp() {
        merchantService = new MerchantService(merchantRepository, webClientBuilder);
    }

    @Test
    void registerMerchant_ShouldCreatePendingMerchant() {
        // Arrange
        UUID userId = UUID.randomUUID();
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Org",
                "BR123",
                "test@example.com",
                "+94771234567"
        );

        when(merchantRepository.existsByOwnerUserId(userId)).thenReturn(false);
        when(merchantRepository.save(any(Merchant.class))).thenAnswer(invocation -> {
            Merchant m = invocation.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        // Act
        var response = merchantService.registerMerchant(userId, request);

        // Assert
        assertNotNull(response);
        assertEquals("Test Org", response.orgName());
        assertEquals(MerchantStatus.PENDING, response.status());
        verify(merchantRepository).save(any(Merchant.class));
    }

    @Test
    void registerMerchant_ShouldThrowWhenMerchantAlreadyExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        MerchantRegistrationRequest request = new MerchantRegistrationRequest(
                "Test Org",
                "BR123",
                "test@example.com",
                "+94771234567"
        );

        when(merchantRepository.existsByOwnerUserId(userId)).thenReturn(true);

        // Act & Assert
        assertThrows(MerchantAlreadyExistsException.class,
                () -> merchantService.registerMerchant(userId, request));
        verify(merchantRepository, never()).save(any());
    }
}

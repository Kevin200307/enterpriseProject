package lk.ac.mrt.swiftdrop.merchant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.merchant_service.api.ApiKeyDtos.CreateRequest;
import lk.ac.mrt.swiftdrop.merchant_service.domain.ApiKey;
import lk.ac.mrt.swiftdrop.merchant_service.domain.Merchant;
import lk.ac.mrt.swiftdrop.merchant_service.domain.MerchantStatus;
import lk.ac.mrt.swiftdrop.merchant_service.repository.ApiKeyRepository;
import lk.ac.mrt.swiftdrop.merchant_service.repository.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {
    @Mock ApiKeyRepository keys;
    @Mock MerchantRepository merchants;
    ApiKeyService service;
    Merchant merchant;

    @BeforeEach void setup() {
        merchant = Merchant.builder().id(UUID.randomUUID()).ownerUserId(UUID.randomUUID()).status(MerchantStatus.APPROVED).build();
        service = new ApiKeyService(keys, merchants, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        when(merchants.findByOwnerUserId(merchant.getOwnerUserId())).thenReturn(Optional.of(merchant));
    }

    @Test void createsOneTimePlaintextButStoresOnlyHash() {
        when(keys.save(any())).thenAnswer(inv -> { ApiKey k = inv.getArgument(0); k.setId(UUID.randomUUID()); k.setCreatedAt(Instant.now()); return k; });
        var created = service.create(merchant.getOwnerUserId(), new CreateRequest("prod", Set.of("parcels:read"), null));
        ApiKey stored = mockingDetails(keys).getInvocations().stream().filter(i -> i.getMethod().getName().equals("save"))
                .map(i -> (ApiKey) i.getArgument(0)).findFirst().orElseThrow();
        assertTrue(created.key().startsWith("sdk_"));
        assertNotEquals(created.key(), stored.getKeyHash());
        assertEquals(64, stored.getKeyHash().length());
    }

    @Test void rejectsUnknownScopeAndCrossMerchantRevoke() {
        assertThrows(IllegalArgumentException.class, () -> service.create(merchant.getOwnerUserId(), new CreateRequest("bad", Set.of("admin:*"), null)));
        UUID keyId = UUID.randomUUID();
        when(keys.findByIdAndMerchantId(keyId, merchant.getId())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.revoke(merchant.getOwnerUserId(), keyId));
    }
}

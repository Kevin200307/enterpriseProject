package lk.ac.mrt.swiftdrop.merchant_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.PackageSize;
import lk.ac.mrt.swiftdrop.merchant_service.api.MerchantOrderDtos.*;
import lk.ac.mrt.swiftdrop.merchant_service.domain.*;
import lk.ac.mrt.swiftdrop.merchant_service.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@ExtendWith(MockitoExtension.class)
class MerchantOrderServiceTest {
    @Mock MerchantRepository merchants;
    @Mock MerchantOrderRepository orders;
    Merchant merchant;
    Address pickup = new Address("Sender", "+94770000000", "1 Main", "Colombo", "00100", "CMB");
    Address dropoff = new Address("Receiver", "+94771111111", "2 Lake", "Kandy", "20000", "KDY");

    @BeforeEach void setup() {
        merchant = Merchant.builder().id(UUID.randomUUID()).ownerUserId(UUID.randomUUID()).status(MerchantStatus.APPROVED).build();
        when(merchants.findByOwnerUserId(merchant.getOwnerUserId())).thenReturn(Optional.of(merchant));
    }

    @Test void createsParcelAndSnapshotsFee() {
        DisposableServer server = HttpServer.create().host("127.0.0.1").port(0).route(r -> r.post("/api/v1/parcels", (req,res) -> {
            assertNotNull(req.requestHeaders().get("Idempotency-Key"));
            assertEquals("Bearer token", req.requestHeaders().get("Authorization"));
            return res.header("Content-Type", "application/json").sendString(Mono.just("{\"id\":\""+UUID.randomUUID()+"\",\"trackingNumber\":\"SD-12345678\",\"status\":\"CREATED\",\"fee\":725.00,\"currency\":\"LKR\"}"));
        })).bindNow();
        try {
            when(orders.findByMerchantIdAndMerchantRef(merchant.getId(), "ERP-1")).thenReturn(Optional.empty());
            when(orders.save(any())).thenAnswer(i -> { MerchantOrder o=i.getArgument(0); o.setId(UUID.randomUUID()); return o; });
            var service = new MerchantOrderService(merchants, orders, WebClient.builder(), "http://127.0.0.1:"+server.port());
            var result = service.create(merchant.getOwnerUserId(), new Create("ERP-1", pickup, dropoff, PackageSize.SMALL, BigDecimal.ONE), "token");
            assertEquals(new BigDecimal("725.00"), result.fee());
            assertEquals("Kandy", result.destinationCity());
        } finally { server.disposeNow(); }
    }

    @Test void duplicateReferenceReturnsSameOrderWithoutCallingParcel() {
        MerchantOrder existing = MerchantOrder.builder().id(UUID.randomUUID()).merchant(merchant).merchantRef("ERP-2")
                .parcelId(UUID.randomUUID()).trackingNumber("SD-12345678").status("CREATED").destinationCity("Kandy")
                .fee(BigDecimal.TEN).currency("LKR").requestHash(hashFor(new Create("ERP-2", pickup, dropoff, PackageSize.SMALL, BigDecimal.ONE))).build();
        when(orders.findByMerchantIdAndMerchantRef(merchant.getId(), "ERP-2")).thenReturn(Optional.of(existing));
        var service = new MerchantOrderService(merchants, orders, WebClient.builder(), "http://127.0.0.1:1");
        assertEquals(existing.getId(), service.create(merchant.getOwnerUserId(), new Create("ERP-2", pickup, dropoff, PackageSize.SMALL, BigDecimal.ONE), "token").id());
    }

    @Test void getDoesNotLeakAnotherMerchantsOrder() {
        UUID id=UUID.randomUUID(); when(orders.findByIdAndMerchantId(id, merchant.getId())).thenReturn(Optional.empty());
        var service = new MerchantOrderService(merchants, orders, WebClient.builder(), "http://127.0.0.1:1");
        assertThrows(ResponseStatusException.class, () -> service.get(merchant.getOwnerUserId(), id));
    }

    private static String hashFor(Create value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(Exception e) { throw new RuntimeException(e); }
    }
}

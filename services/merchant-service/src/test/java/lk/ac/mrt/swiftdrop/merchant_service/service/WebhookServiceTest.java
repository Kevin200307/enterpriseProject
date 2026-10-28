package lk.ac.mrt.swiftdrop.merchant_service.service;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.*;
import lk.ac.mrt.swiftdrop.merchant_service.domain.*;
import lk.ac.mrt.swiftdrop.merchant_service.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    @Mock MerchantRepository merchants; @Mock WebhookDeliveryRepository deliveries;
    @Test void disabledEndpointAndDuplicateEventProduceNoDelivery() {
        UUID merchantId=UUID.randomUUID(); UUID eventId=UUID.randomUUID();
        Merchant disabled=Merchant.builder().id(merchantId).status(MerchantStatus.APPROVED).build();
        var service=new WebhookService(merchants,deliveries,WebClient.builder(),new WebhookCrypto("master"),JsonMapper.builder().build());
        when(merchants.findById(merchantId)).thenReturn(Optional.of(disabled));
        service.accept(event(eventId,merchantId)); verifyNoInteractions(deliveries);

        disabled.setWebhookUrl("https://hooks.example.com/swiftdrop"); disabled.setWebhookSecretHash("encrypted");
        when(deliveries.findByEventIdAndMerchantId(eventId,merchantId)).thenReturn(Optional.of(new WebhookDelivery()));
        service.accept(event(eventId,merchantId)); verify(deliveries,never()).save(any());
    }
    @Test void retryIsBoundedAndBecomesDeadAtMaxAttempts() {
        Merchant m=Merchant.builder().id(UUID.randomUUID()).webhookSecretHash(new WebhookCrypto("master").encrypt("secret")).build();
        WebhookDelivery d=WebhookDelivery.builder().merchant(m).url("http://127.0.0.1:1/hook").payload("{}").attempts(4).build();
        var service=new WebhookService(merchants,deliveries,WebClient.builder(),new WebhookCrypto("master"),JsonMapper.builder().build());
        service.deliver(d);
        assertEquals(5,d.getAttempts()); assertEquals(WebhookDeliveryState.DEAD,d.getState()); assertNull(d.getNextRetryAt());
        assertEquals(java.time.Duration.ofSeconds(30),WebhookService.retryDelay(1));
        assertEquals(java.time.Duration.ofSeconds(480),WebhookService.retryDelay(5));
    }
    @Test void replayCannotReadAnotherMerchantsDelivery() {
        UUID owner=UUID.randomUUID(); Merchant m=Merchant.builder().id(UUID.randomUUID()).ownerUserId(owner).build(); UUID id=UUID.randomUUID();
        when(merchants.findByOwnerUserId(owner)).thenReturn(Optional.of(m)); when(deliveries.findByIdAndMerchantId(id,m.getId())).thenReturn(Optional.empty());
        var service=new WebhookService(merchants,deliveries,WebClient.builder(),new WebhookCrypto("master"),JsonMapper.builder().build());
        assertThrows(IllegalArgumentException.class,()->service.replay(owner,id));
    }
    private static EventEnvelope<ParcelStatusChangedV2> event(UUID id, UUID merchantId) {
        return new EventEnvelope<>(id,ParcelStatusChangedV2.EVENT_TYPE,2,java.time.Instant.EPOCH,"parcel-service",new ParcelStatusChangedV2(UUID.randomUUID(),"SD-12345678",UUID.randomUUID(),merchantId,null,ParcelStatus.CREATED,ParcelStatus.PICKED_UP,UUID.randomUUID(),null,null));
    }
}

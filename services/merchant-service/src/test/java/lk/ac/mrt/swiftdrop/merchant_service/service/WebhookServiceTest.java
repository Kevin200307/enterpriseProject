package lk.ac.mrt.swiftdrop.merchant_service.service;

import static org.mockito.Mockito.*;
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
    private static EventEnvelope<ParcelStatusChangedV2> event(UUID id, UUID merchantId) {
        return new EventEnvelope<>(id,ParcelStatusChangedV2.EVENT_TYPE,2,java.time.Instant.EPOCH,"parcel-service",new ParcelStatusChangedV2(UUID.randomUUID(),"SD-12345678",UUID.randomUUID(),merchantId,null,ParcelStatus.CREATED,ParcelStatus.PICKED_UP,UUID.randomUUID(),null,null));
    }
}

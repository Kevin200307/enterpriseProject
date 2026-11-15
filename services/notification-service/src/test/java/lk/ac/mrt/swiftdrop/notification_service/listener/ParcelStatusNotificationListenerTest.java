package lk.ac.mrt.swiftdrop.notification_service.listener;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.events.EventEnvelope;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV2;
import lk.ac.mrt.swiftdrop.notification_service.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParcelStatusNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ParcelStatusNotificationListener listener;

    @Test
    @DisplayName("Should pass incoming event envelope to notificationService")
    void shouldForwardEventToNotificationService() {
        UUID parcelId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        ParcelStatusChangedV2 payload = new ParcelStatusChangedV2(
                parcelId, "SD-11223344", customerId, null, null,
                ParcelStatus.CREATED, ParcelStatus.PICKED_UP, UUID.randomUUID(), null, null);
        EventEnvelope<ParcelStatusChangedV2> envelope = EventEnvelope.of(
                ParcelStatusChangedV2.EVENT_TYPE, ParcelStatusChangedV2.VERSION, "parcel-service", payload);

        listener.onParcelStatusChanged(envelope);

        verify(notificationService, times(1)).handleParcelStatusChanged(envelope);
    }
}

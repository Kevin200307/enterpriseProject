package lk.ac.mrt.swiftdrop.merchant_service.service;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import lk.ac.mrt.swiftdrop.merchant_service.domain.*;
import lk.ac.mrt.swiftdrop.merchant_service.repository.WebhookDeliveryRepository;
import org.junit.jupiter.api.Test;
class WebhookRetrySchedulerTest {
 @Test void recoversDuePendingDeliveriesAfterRestart(){ WebhookDeliveryRepository repo=mock(WebhookDeliveryRepository.class); WebhookService service=mock(WebhookService.class); WebhookDelivery due=new WebhookDelivery(); when(repo.findByStateAndNextRetryAtBefore(eq(WebhookDeliveryState.PENDING),any(Instant.class))).thenReturn(List.of(due)); new WebhookRetryScheduler(repo,service).retryDue(); verify(service).deliver(due); }
}

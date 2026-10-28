package lk.ac.mrt.swiftdrop.merchant_service.service;

import java.time.Instant;
import lk.ac.mrt.swiftdrop.merchant_service.domain.WebhookDeliveryState;
import lk.ac.mrt.swiftdrop.merchant_service.repository.WebhookDeliveryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WebhookRetryScheduler {
    private final WebhookDeliveryRepository deliveries; private final WebhookService service;
    public WebhookRetryScheduler(WebhookDeliveryRepository deliveries, WebhookService service){this.deliveries=deliveries;this.service=service;}
    @Scheduled(fixedDelayString="${merchant.webhook.retry-scan-ms:10000}")
    public void retryDue(){ deliveries.findByStateAndNextRetryAtBefore(WebhookDeliveryState.PENDING, Instant.now()).forEach(service::deliver); }
}

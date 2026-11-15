package lk.ac.mrt.swiftdrop.dispatch_service.service;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component @ConditionalOnProperty(name="dispatch.retry-enabled",havingValue="true",matchIfMissing=true)
public class AssignmentRetrier {
    private static final Logger LOG = LoggerFactory.getLogger(AssignmentRetrier.class);
    private final AssignmentTransactions transactions;
    private final AssignmentService service;
    public AssignmentRetrier(AssignmentTransactions transactions, AssignmentService service) { this.transactions=transactions; this.service=service; }
    @Scheduled(fixedDelayString="${dispatch.retry-delay-ms:1000}") public void retry() {
        for (var id : transactions.pending()) {
            try { service.retry(id); }
            catch (RuntimeException ex) { LOG.warn("Assignment command {} retry failed ({})",id,ex.getClass().getSimpleName()); }
        }
    }
}

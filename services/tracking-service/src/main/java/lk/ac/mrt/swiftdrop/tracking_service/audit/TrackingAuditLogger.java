package lk.ac.mrt.swiftdrop.tracking_service.audit;

import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audit log component for tracking access to private customer/courier PII.
 */
@Slf4j
@Component
public class TrackingAuditLogger {

    public void logPiiAccess(String trackingNumber, UUID userId, Set<String> roles, String action) {
        log.info("AUDIT PII_ACCESS: user={} roles={} action={} trackingNumber={}",
                userId, roles, action, trackingNumber);
    }
}

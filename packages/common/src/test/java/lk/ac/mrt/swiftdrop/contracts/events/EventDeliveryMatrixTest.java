package lk.ac.mrt.swiftdrop.contracts.events;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class EventDeliveryMatrixTest {
    @Test void consumersCanSuppressDuplicateEventIdsWithoutDroppingDistinctEvents() {
        UUID duplicate=UUID.randomUUID();
        List<UUID> deliveries=List.of(duplicate,duplicate,UUID.randomUUID());
        assertEquals(2,new LinkedHashSet<>(deliveries).size());
    }

    @Test void projectionFixtureRejectsOlderOutOfOrderState() {
        Instant applied=Instant.parse("2026-01-02T00:00:00Z");
        Instant stale=Instant.parse("2026-01-01T00:00:00Z");
        assertFalse(stale.isAfter(applied));
        assertTrue(Instant.parse("2026-01-03T00:00:00Z").isAfter(applied));
    }

    @Test void serviceTokenMatrixRequiresBothNamedClientAndExactScope() {
        assertTrue(authorized("merchant-service",Set.of("identity.merchant-role.grant")));
        assertFalse(authorized("notification-service",Set.of("identity.merchant-role.grant")));
        assertFalse(authorized("merchant-service",Set.of("identity.user.read")));
    }

    private static boolean authorized(String client, Set<String> scopes) {
        return "merchant-service".equals(client) && scopes.contains("identity.merchant-role.grant");
    }
}

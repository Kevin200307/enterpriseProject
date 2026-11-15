package lk.ac.mrt.swiftdrop.parcel_service;

import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;
import lk.ac.mrt.swiftdrop.parcel_service.service.ParcelStateMachine;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ParcelStateMachineTest {
    @Test void onlyTheDocumentedEdgesAreAllowed() {
        var allowed = Set.of("CREATED:PICKED_UP", "CREATED:CANCELLED", "PICKED_UP:IN_TRANSIT", "IN_TRANSIT:OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY:DELIVERED", "OUT_FOR_DELIVERY:FAILED", "FAILED:OUT_FOR_DELIVERY");
        for (var from : ParcelStatus.values()) for (var to : ParcelStatus.values()) {
            if (allowed.contains(from + ":" + to)) {
                assertThatCode(() -> ParcelStateMachine.validate(from,to)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> ParcelStateMachine.validate(from,to)).isInstanceOf(ParcelException.class);
            }
        }
    }
}

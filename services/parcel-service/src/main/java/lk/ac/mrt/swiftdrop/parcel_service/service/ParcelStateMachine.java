package lk.ac.mrt.swiftdrop.parcel_service.service;

import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.domain.ParcelStatus;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;

public final class ParcelStateMachine {
    private static final Map<ParcelStatus, Set<ParcelStatus>> TRANSITIONS = Map.of(
        ParcelStatus.CREATED, Set.of(ParcelStatus.PICKED_UP, ParcelStatus.CANCELLED),
        ParcelStatus.PICKED_UP, Set.of(ParcelStatus.IN_TRANSIT),
        ParcelStatus.IN_TRANSIT, Set.of(ParcelStatus.OUT_FOR_DELIVERY),
        ParcelStatus.OUT_FOR_DELIVERY, Set.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED),
        ParcelStatus.FAILED, Set.of(ParcelStatus.OUT_FOR_DELIVERY),
        ParcelStatus.DELIVERED, Set.of(),
        ParcelStatus.CANCELLED, Set.of());
    private ParcelStateMachine() {}
    public static void validate(ParcelStatus from, ParcelStatus to) {
        if (to == null || !TRANSITIONS.get(from).contains(to)) {
            throw new ParcelException(ErrorCode.INVALID_STATUS_TRANSITION,
                "Cannot change parcel status from " + from + " to " + to);
        }
    }
}

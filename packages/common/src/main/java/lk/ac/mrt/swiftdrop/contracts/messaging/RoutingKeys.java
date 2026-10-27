package lk.ac.mrt.swiftdrop.contracts.messaging;

import lk.ac.mrt.swiftdrop.contracts.events.ParcelStatusChangedV1;
import lk.ac.mrt.swiftdrop.contracts.events.ParcelDispatchChangedV1;

/** Routing key convention: {@code <eventType>.v<version>}. All values are compile-time constants, so they work in annotations. */
public final class RoutingKeys {

    public static final String PARCEL_DISPATCH_CHANGED_V1 =
            ParcelDispatchChangedV1.EVENT_TYPE + ".v" + ParcelDispatchChangedV1.VERSION;

    public static final String PARCEL_STATUS_CHANGED_V1 =
            ParcelStatusChangedV1.EVENT_TYPE + ".v" + ParcelStatusChangedV1.VERSION;

    /** Binding pattern matching every parcel event, any version. */
    public static final String ALL_PARCEL_EVENTS = "parcel.#";

    private RoutingKeys() {
    }
}

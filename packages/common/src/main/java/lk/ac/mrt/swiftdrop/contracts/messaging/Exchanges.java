package lk.ac.mrt.swiftdrop.contracts.messaging;

public final class Exchanges {

    /** Topic exchange for all parcel lifecycle events. */
    public static final String PARCEL_EVENTS = "parcel.events";

    /** Dead-letter exchange that every consumer queue routes rejected messages to. */
    public static final String DEAD_LETTER = "swiftdrop.dlx";

    private Exchanges() {
    }
}

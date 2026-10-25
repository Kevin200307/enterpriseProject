package lk.ac.mrt.swiftdrop.contracts.messaging;

/**
 * Queue naming convention: {@code <consuming-service>.<routing-key>}. Each consumer owns its
 * own queue so every service receives its own copy of each event.
 */
public final class Queues {

    public static final String DEAD_LETTER_SUFFIX = ".dlq";

    public static final String NOTIFICATION_PARCEL_STATUS_CHANGED =
            "notification." + RoutingKeys.PARCEL_STATUS_CHANGED_V1;

    public static final String TRACKING_PARCEL_STATUS_CHANGED =
            "tracking." + RoutingKeys.PARCEL_STATUS_CHANGED_V1;

    public static final String TRACKING_PARCEL_SNAPSHOT =
            "tracking." + RoutingKeys.PARCEL_TRACKING_SNAPSHOT_V1;

    public static final String MERCHANT_WEBHOOK_PARCEL_STATUS_CHANGED =
            "merchant-webhook." + RoutingKeys.PARCEL_STATUS_CHANGED_V1;

    private Queues() {
    }

    public static String deadLetterOf(String queueName) {
        return queueName + DEAD_LETTER_SUFFIX;
    }
}

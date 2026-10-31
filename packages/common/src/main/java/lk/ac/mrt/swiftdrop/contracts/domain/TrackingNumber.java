package lk.ac.mrt.swiftdrop.contracts.domain;

import java.util.regex.Pattern;

/**
 * SwiftDrop tracking number format and validation rules.
 * Tracking numbers use the parcel service's zero-padded base-36 sequence format.
 */
public final class TrackingNumber {

    public static final String REGEX = "^SD-[0-9A-Z]{8}$";
    public static final Pattern PATTERN = Pattern.compile(REGEX);

    private TrackingNumber() {
    }

    public static boolean isValid(String trackingNumber) {
        return trackingNumber != null && PATTERN.matcher(trackingNumber).matches();
    }

    public static void validate(String trackingNumber) {
        if (!isValid(trackingNumber)) {
            throw new IllegalArgumentException("Invalid tracking number format: " + trackingNumber);
        }
    }
}

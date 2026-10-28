package lk.ac.mrt.swiftdrop.contracts.domain;

import java.util.regex.Pattern;

/**
 * SwiftDrop tracking number format and validation rules.
 * Strictly adheres to {@code ^SD-[A-HJ-NP-Z2-9]{8}$}.
 * Excludes ambiguous characters (I, L, O, 0, 1).
 */
public final class TrackingNumber {

    public static final String REGEX = "^SD-[A-HJ-NP-Z2-9]{8}$";
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

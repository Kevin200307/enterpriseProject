package lk.ac.mrt.swiftdrop.contracts.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TrackingNumberTest {

    @Test
    void acceptsZeroPaddedBase36NumbersGeneratedForNewParcels() {
        assertTrue(TrackingNumber.isValid("SD-00000001"));
        assertTrue(TrackingNumber.isValid("SD-0000000Z"));
        assertTrue(TrackingNumber.isValid("SD-ABCD2345"));
    }

    @Test
    void rejectsNumbersOutsideTheGeneratedFormat() {
        assertFalse(TrackingNumber.isValid("SD-INVALID"));
        assertFalse(TrackingNumber.isValid("SD-0000000_"));
        assertFalse(TrackingNumber.isValid(null));
    }
}

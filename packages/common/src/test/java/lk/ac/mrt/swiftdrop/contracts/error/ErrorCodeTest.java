package lk.ac.mrt.swiftdrop.contracts.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ErrorCodeTest {

    @Test
    void everyCodeMapsToAnErrorHttpStatus() {
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(code.httpStatus() >= 400 && code.httpStatus() <= 599, code + " -> " + code.httpStatus());
        }
    }

    @Test
    void typeUrisAreUniqueAndStable() {
        Set<URI> types = Arrays.stream(ErrorCode.values()).map(ErrorCode::type).collect(Collectors.toSet());

        assertEquals(ErrorCode.values().length, types.size());
        assertEquals(URI.create("urn:swiftdrop:error:validation-failed"), ErrorCode.VALIDATION_FAILED.type());
    }
}

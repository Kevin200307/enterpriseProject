package lk.ac.mrt.swiftdrop.parcel_service;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lk.ac.mrt.swiftdrop.parcel_service.storage.MinioProofStorage;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MinioPresignedUrlTest {
    @Test void signsAReadOnlyUrlWithFiveMinuteExpiryUsingThePublicHostname() {
        var storage = new MinioProofStorage("http://internal-storage:9000", "https://proofs.example.test",
            "test-access-key", "test-secret-key-for-unit-tests", "parcel-proofs", "us-east-1");
        URI url = URI.create(storage.presign("proofs/parcel-id/proof-id", 300));
        assertThat(url.getHost()).isEqualTo("proofs.example.test");
        assertThat(url.getPath()).isEqualTo("/parcel-proofs/proofs/parcel-id/proof-id");
        Map<String,String> query = new HashMap<>();
        for (String field : url.getRawQuery().split("&")) {
            String[] pair = field.split("=",2);
            query.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair[1],StandardCharsets.UTF_8));
        }
        assertThat(query.get("X-Amz-Expires")).isEqualTo("300");
        assertThat(query.get("X-Amz-SignedHeaders")).isEqualTo("host");
        assertThat(query.get("X-Amz-Signature")).matches("[a-f0-9]{64}");
        assertThat(query.get("response-cache-control")).isEqualTo("no-store");
        assertThat(query.get("response-content-disposition")).startsWith("attachment");
    }
}

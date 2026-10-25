package lk.ac.mrt.swiftdrop.parcel_service;

import io.minio.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import lk.ac.mrt.swiftdrop.parcel_service.storage.MinioProofStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.assertj.core.api.Assertions.*;

/** Uses a fresh private bucket; opt in only against your local/disposable MinIO. */
@EnabledIfEnvironmentVariable(named="PARCEL_LIVE_STORAGE_TESTS",matches="true")
class MinioLiveIntegrationTest {
    @Test void storageActuallyRejectsAnExpiredPresignedUrl() throws Exception {
        String endpoint = System.getenv("MINIO_ENDPOINT");
        String access = System.getenv("MINIO_ACCESS_KEY");
        String secret = System.getenv("MINIO_SECRET_KEY");
        String bucket = "parcel-test-" + UUID.randomUUID();
        var admin = MinioClient.builder().endpoint(endpoint).credentials(access,secret).region("us-east-1").build();
        var store = new MinioProofStorage(endpoint,endpoint,access,secret,bucket,"us-east-1");
        admin.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        String key = "proofs/test-proof";
        try {
            store.put(key,new byte[]{1,2,3},"application/octet-stream");
            assertThat(store.get(key)).containsExactly((byte)1,(byte)2,(byte)3);
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI url = URI.create(store.presign(key,2));
            var request = HttpRequest.newBuilder(url).timeout(Duration.ofSeconds(5)).GET().build();
            assertThat(http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
            Thread.sleep(3100);
            assertThat(http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
        } finally {
            store.delete(key);
            admin.removeBucket(RemoveBucketArgs.builder().bucket(bucket).build());
            admin.close();
        }
    }
}

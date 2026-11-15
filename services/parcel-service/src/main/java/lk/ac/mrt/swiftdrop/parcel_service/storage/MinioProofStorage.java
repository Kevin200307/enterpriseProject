package lk.ac.mrt.swiftdrop.parcel_service.storage;

import io.minio.*;
import io.minio.http.Method;
import java.io.ByteArrayInputStream;
import java.util.Map;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.parcel_service.api.ParcelException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioProofStorage implements ProofStorage {
    private final MinioClient internal;
    private final MinioClient external;
    private final String bucket;

    public MinioProofStorage(@Value("${parcel.storage.endpoint}") String endpoint,
        @Value("${parcel.storage.public-endpoint}") String publicEndpoint,
        @Value("${parcel.storage.access-key}") String accessKey,
        @Value("${parcel.storage.secret-key}") String secretKey,
        @Value("${parcel.storage.bucket}") String bucket,
        @Value("${parcel.storage.region}") String region) {
        this.internal = MinioClient.builder().endpoint(endpoint).credentials(accessKey,secretKey).region(region).build();
        // Presigning must use the hostname the browser actually reaches; never rewrite a signed URL.
        this.external = MinioClient.builder().endpoint(publicEndpoint).credentials(accessKey,secretKey).region(region).build();
        this.internal.setTimeout(3000, 5000, 5000);
        this.bucket = bucket;
    }
    @Override public void put(String key, byte[] content, String contentType) {
        try (var input = new ByteArrayInputStream(content)) {
            internal.putObject(PutObjectArgs.builder().bucket(bucket).object(key)
                .stream(input,content.length,-1).contentType(contentType).build());
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public byte[] get(String key) {
        try (var input = internal.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return input.readNBytes(5 * 1024 * 1024 + 1);
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public String presign(String key, int expirySeconds) {
        try {
            return external.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
                .bucket(bucket).object(key).expiry(expirySeconds)
                .extraQueryParams(Map.of("response-content-disposition", "attachment; filename=delivery-proof",
                    "response-cache-control", "no-store")).build());
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public void delete(String key) {
        try { internal.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build()); }
        catch (Exception ex) { throw unavailable(); }
    }
    private ParcelException unavailable() {
        return new ParcelException(ErrorCode.UPSTREAM_UNAVAILABLE, "Delivery proof storage is temporarily unavailable");
    }
}

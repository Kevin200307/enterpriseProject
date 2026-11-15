package lk.ac.mrt.swiftdrop.parcel_service.storage;

public interface ProofStorage {
    void put(String key, byte[] content, String contentType);
    byte[] get(String key);
    String presign(String key, int expirySeconds);
    void delete(String key);
}

package in.pahadijodi.media.port;

/** Object storage. Fake = local filesystem; production = S3 (adapter to be added). */
public interface BlobStore {
    void put(String key, byte[] data);
    byte[] get(String key);
    void delete(String key);
}

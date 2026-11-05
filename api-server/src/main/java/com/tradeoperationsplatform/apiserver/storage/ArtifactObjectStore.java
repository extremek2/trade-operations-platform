package com.tradeoperationsplatform.apiserver.storage;

public interface ArtifactObjectStore {
    record StoredObject(String backend, String bucket, String key, String etag) {}

    boolean isEnabled();
    StoredObject put(String key, String contentType, byte[] content, String sha256);
    byte[] get(String key);
    void delete(String key);
}

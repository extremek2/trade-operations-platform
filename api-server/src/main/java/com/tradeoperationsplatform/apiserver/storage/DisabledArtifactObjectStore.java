package com.tradeoperationsplatform.apiserver.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.object-storage.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledArtifactObjectStore implements ArtifactObjectStore {
    @Override public boolean isEnabled() { return false; }
    @Override public StoredObject put(String key, String contentType, byte[] content, String sha256) {
        throw new ObjectStorageException("문서 저장소가 활성화되지 않았습니다.");
    }
    @Override public byte[] get(String key) { throw new ObjectStorageException("문서 저장소가 활성화되지 않았습니다."); }
    @Override public void delete(String key) { throw new ObjectStorageException("문서 저장소가 활성화되지 않았습니다."); }
}

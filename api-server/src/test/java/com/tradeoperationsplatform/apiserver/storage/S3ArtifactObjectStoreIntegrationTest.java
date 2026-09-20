package com.tradeoperationsplatform.apiserver.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "MINIO_TEST_ENDPOINT", matches = ".+")
class S3ArtifactObjectStoreIntegrationTest {
    private final S3ArtifactObjectStore store = new S3ArtifactObjectStore(
            System.getenv("MINIO_TEST_ENDPOINT"), "us-east-1",
            System.getenv().getOrDefault("MINIO_TEST_ACCESS_KEY", "tradeops-dev"),
            System.getenv().getOrDefault("MINIO_TEST_SECRET_KEY", "tradeops-dev-secret"),
            "trade-ops-integration", true, true);

    @AfterEach void close() { store.close(); }

    @Test
    void roundTripsBytesAndMetadataAgainstMinio() throws Exception {
        byte[] original = ("supplier-document-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        String key = "organizations/test/supplier-documents/" + UUID.randomUUID() + "/original";

        ArtifactObjectStore.StoredObject stored = store.put(key, "application/pdf", original, hash);

        assertThat(stored.backend()).isEqualTo("S3");
        assertThat(stored.bucket()).isEqualTo("trade-ops-integration");
        assertThat(stored.key()).isEqualTo(key);
        assertThat(stored.etag()).isNotBlank();
        assertThat(store.get(key)).isEqualTo(original);

        store.delete(key);
        assertThatThrownBy(() -> store.get(key)).isInstanceOf(ObjectStorageException.class);
    }
}

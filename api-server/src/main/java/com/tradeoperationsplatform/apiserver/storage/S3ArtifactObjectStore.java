package com.tradeoperationsplatform.apiserver.storage;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "app.object-storage.enabled", havingValue = "true")
public class S3ArtifactObjectStore implements ArtifactObjectStore {
    private final S3Client client;
    private final String bucket;
    private final boolean createBucket;
    private volatile boolean bucketReady;

    public S3ArtifactObjectStore(@Value("${app.object-storage.endpoint}") String endpoint,
                                 @Value("${app.object-storage.region:us-east-1}") String region,
                                 @Value("${app.object-storage.access-key}") String accessKey,
                                 @Value("${app.object-storage.secret-key}") String secretKey,
                                 @Value("${app.object-storage.bucket}") String bucket,
                                 @Value("${app.object-storage.path-style:true}") boolean pathStyle,
                                 @Value("${app.object-storage.create-bucket:false}") boolean createBucket) {
        if (endpoint == null || endpoint.isBlank() || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank() || bucket == null || bucket.isBlank())
            throw new IllegalArgumentException("활성 문서 저장소의 endpoint, credentials, bucket은 필수입니다.");
        this.bucket = bucket;
        this.createBucket = createBucket;
        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Override public boolean isEnabled() { return true; }

    @Override
    public StoredObject put(String key, String contentType, byte[] content, String sha256) {
        requireKey(key); requireContent(content); ensureBucket();
        try {
            PutObjectResponse response = client.putObject(PutObjectRequest.builder()
                    .bucket(bucket).key(key).contentType(contentType)
                    .metadata(Map.of("sha256", sha256)).build(), RequestBody.fromBytes(content));
            return new StoredObject("S3", bucket, key, trimQuotes(response.eTag()));
        } catch (S3Exception e) {
            throw new ObjectStorageException("문서 원본을 객체 저장소에 기록하지 못했습니다.", e);
        }
    }

    @Override
    public byte[] get(String key) {
        requireKey(key); ensureBucket();
        try { return client.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray(); }
        catch (S3Exception e) { throw new ObjectStorageException("객체 저장소에서 문서 원본을 읽지 못했습니다.", e); }
    }

    @Override
    public void delete(String key) {
        requireKey(key); ensureBucket();
        try { client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build()); }
        catch (S3Exception e) { throw new ObjectStorageException("객체 저장소에서 문서 원본을 삭제하지 못했습니다.", e); }
    }

    private synchronized void ensureBucket() {
        if (bucketReady) return;
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            bucketReady = true;
        } catch (S3Exception e) {
            if (!createBucket || e.statusCode() != 404)
                throw new ObjectStorageException("문서 저장소 bucket을 확인하지 못했습니다.", e);
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                bucketReady = true;
            } catch (S3Exception creationError) {
                throw new ObjectStorageException("문서 저장소 bucket을 준비하지 못했습니다.", creationError);
            }
        }
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank() || key.startsWith("/") || key.contains(".."))
            throw new IllegalArgumentException("객체 키가 올바르지 않습니다.");
    }
    private void requireContent(byte[] content) {
        if (content == null || content.length == 0) throw new IllegalArgumentException("빈 객체는 저장할 수 없습니다.");
    }
    private String trimQuotes(String value) { return value == null ? null : value.replace("\"", ""); }
    @PreDestroy void close() { client.close(); }
}

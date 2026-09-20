package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_artifact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SourceArtifact {
    public enum SourceType { MANUAL, CSV, XLSX, PDF, IMAGE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private BusinessPartner supplier;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private SourceType sourceType;
    @Column(name = "source_reference") private String sourceReference;
    @Column(name = "original_text", columnDefinition = "text") private String originalText;
    @Column(name = "original_file_name") private String originalFileName;
    @Column(name = "binary_content") private byte[] binaryContent;
    @Column(name = "file_size") private Long fileSize;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
    @Column(name = "storage_backend", length = 20) private String storageBackend;
    @Column(name = "object_bucket") private String objectBucket;
    @Column(name = "object_key", length = 1000) private String objectKey;
    @Column(name = "object_etag") private String objectEtag;
    @Column(name = "received_at", nullable = false, updatable = false) private LocalDateTime receivedAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;

    public SourceArtifact(Organization organization, BusinessPartner supplier, AppUser createdBy,
                          String sourceReference, String originalText, String contentHash) {
        if (originalText == null || originalText.isBlank()) throw new IllegalArgumentException("원본 내용은 필수입니다.");
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.supplier = supplier;
        this.createdBy = createdBy;
        this.sourceType = SourceType.MANUAL;
        this.sourceReference = sourceReference == null || sourceReference.isBlank() ? null : sourceReference.trim();
        this.originalText = originalText;
        this.contentType = "text/plain";
        this.contentHash = contentHash;
    }

    public SourceArtifact(Organization organization, BusinessPartner supplier, AppUser createdBy,
                          SourceType sourceType, String sourceReference, String originalFileName,
                          String contentType, long fileSize, String contentHash,
                          String storageBackend, String objectBucket, String objectKey, String objectEtag) {
        if (sourceType != SourceType.PDF && sourceType != SourceType.IMAGE)
            throw new IllegalArgumentException("문서 원본 형식이 올바르지 않습니다.");
        if (originalFileName == null || originalFileName.isBlank() || fileSize < 1)
            throw new IllegalArgumentException("문서 원본 파일정보는 필수입니다.");
        if (!"S3".equals(storageBackend) || objectBucket == null || objectBucket.isBlank()
                || objectKey == null || objectKey.isBlank())
            throw new IllegalArgumentException("문서 객체 저장 위치는 필수입니다.");
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.supplier = supplier;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference == null || sourceReference.isBlank() ? null : sourceReference.trim();
        this.originalFileName = originalFileName.trim();
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.contentHash = contentHash;
        this.storageBackend = storageBackend;
        this.objectBucket = objectBucket;
        this.objectKey = objectKey;
        this.objectEtag = objectEtag;
    }

    public SourceArtifact(Organization organization, BusinessPartner supplier, AppUser createdBy,
                          SourceType sourceType, String sourceReference, String originalFileName,
                          String contentType, byte[] binaryContent, String contentHash) {
        if (sourceType != SourceType.CSV && sourceType != SourceType.XLSX)
            throw new IllegalArgumentException("파일 원본 형식이 올바르지 않습니다.");
        if (originalFileName == null || originalFileName.isBlank())
            throw new IllegalArgumentException("원본 파일명은 필수입니다.");
        if (binaryContent == null || binaryContent.length == 0)
            throw new IllegalArgumentException("원본 파일 내용은 필수입니다.");
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.supplier = supplier;
        this.createdBy = createdBy;
        this.sourceType = sourceType;
        this.sourceReference = sourceReference == null || sourceReference.isBlank() ? null : sourceReference.trim();
        this.originalFileName = originalFileName.trim();
        this.contentType = contentType;
        this.binaryContent = binaryContent.clone();
        this.fileSize = (long) binaryContent.length;
        this.contentHash = contentHash;
    }

    @PrePersist void create() { receivedAt = LocalDateTime.now(); }
}

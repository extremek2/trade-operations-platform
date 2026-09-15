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
    public enum SourceType { MANUAL }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private BusinessPartner supplier;
    @Enumerated(EnumType.STRING) @Column(name = "source_type", nullable = false) private SourceType sourceType;
    @Column(name = "source_reference") private String sourceReference;
    @Column(name = "original_text", nullable = false, columnDefinition = "text") private String originalText;
    @Column(name = "content_type", nullable = false) private String contentType;
    @Column(name = "content_hash", nullable = false, length = 64) private String contentHash;
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

    @PrePersist void create() { receivedAt = LocalDateTime.now(); }
}

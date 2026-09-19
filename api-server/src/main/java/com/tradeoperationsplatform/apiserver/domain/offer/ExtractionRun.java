package com.tradeoperationsplatform.apiserver.domain.offer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "extraction_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionRun {
    public enum Method { MANUAL, CSV, XLSX }
    public enum Status { SUCCEEDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_artifact_id") private SourceArtifact sourceArtifact;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Method method;
    @Column(name = "extractor_version", nullable = false) private String extractorVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public ExtractionRun(SourceArtifact sourceArtifact) {
        this.publicId = UUID.randomUUID();
        this.sourceArtifact = sourceArtifact;
        this.method = Method.MANUAL;
        this.extractorVersion = "manual-v1";
        this.status = Status.SUCCEEDED;
    }

    public ExtractionRun(SourceArtifact sourceArtifact, Method method, String extractorVersion) {
        if (method == null || method == Method.MANUAL)
            throw new IllegalArgumentException("파일 추출 방식이 올바르지 않습니다.");
        if (extractorVersion == null || extractorVersion.isBlank())
            throw new IllegalArgumentException("추출기 버전은 필수입니다.");
        this.publicId = UUID.randomUUID();
        this.sourceArtifact = sourceArtifact;
        this.method = method;
        this.extractorVersion = extractorVersion.trim();
        this.status = Status.SUCCEEDED;
    }

    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

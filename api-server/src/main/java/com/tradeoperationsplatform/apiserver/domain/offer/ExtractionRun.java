package com.tradeoperationsplatform.apiserver.domain.offer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "extraction_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExtractionRun {
    public enum Method { MANUAL, CSV, XLSX, DOCUMENT, PDF_TEXT, OCR }
    public enum Status { PENDING, RUNNING, SUCCEEDED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_artifact_id") private SourceArtifact sourceArtifact;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Method method;
    @Column(name = "extractor_version", nullable = false) private String extractorVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "error_message") private String errorMessage;
    @Column(name = "extracted_text", columnDefinition = "text") private String extractedText;
    @Column(precision = 5, scale = 2) private BigDecimal confidence;
    @Column(name = "page_count") private Integer pageCount;
    @Column(name = "preprocessing_applied") private Boolean preprocessingApplied;
    @Column(name = "review_required") private Boolean reviewRequired;
    @Column(name = "result_payload", columnDefinition = "text") private String resultPayload;
    @Column(name = "completed_at") private LocalDateTime completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public ExtractionRun(SourceArtifact sourceArtifact) {
        if (sourceArtifact.getSourceType() != SourceArtifact.SourceType.MANUAL)
            throw new IllegalArgumentException("수동 입력 원본이 올바르지 않습니다.");
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

    public ExtractionRun(SourceArtifact sourceArtifact, boolean document) {
        if (!document || (sourceArtifact.getSourceType() != SourceArtifact.SourceType.PDF
                && sourceArtifact.getSourceType() != SourceArtifact.SourceType.IMAGE))
            throw new IllegalArgumentException("문서 추출 원본이 올바르지 않습니다.");
        this.publicId = UUID.randomUUID();
        this.sourceArtifact = sourceArtifact;
        this.method = Method.DOCUMENT;
        this.extractorVersion = "document-router-v1";
        this.status = Status.PENDING;
    }

    public void start() {
        if (status != Status.PENDING) throw new IllegalStateException("대기 중인 문서 추출만 시작할 수 있습니다.");
        status = Status.RUNNING;
    }

    public void succeed(Method method, String extractorVersion, String extractedText, BigDecimal confidence,
                        int pageCount, boolean preprocessingApplied, boolean reviewRequired, String resultPayload) {
        if (status != Status.RUNNING) throw new IllegalStateException("실행 중인 문서 추출만 완료할 수 있습니다.");
        if (method != Method.PDF_TEXT && method != Method.OCR)
            throw new IllegalArgumentException("문서 추출 방식이 올바르지 않습니다.");
        if (extractedText == null || extractedText.isBlank()) throw new IllegalArgumentException("추출 텍스트가 비어 있습니다.");
        if (confidence == null || confidence.signum() < 0 || confidence.compareTo(new BigDecimal("100")) > 0)
            throw new IllegalArgumentException("문서 추출 신뢰도가 올바르지 않습니다.");
        if (pageCount < 1) throw new IllegalArgumentException("문서 페이지 수가 올바르지 않습니다.");
        this.method = method;
        this.extractorVersion = extractorVersion;
        this.extractedText = extractedText;
        this.confidence = confidence;
        this.pageCount = pageCount;
        this.preprocessingApplied = preprocessingApplied;
        this.reviewRequired = reviewRequired;
        this.resultPayload = resultPayload;
        this.status = Status.SUCCEEDED;
        this.completedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        if (status != Status.RUNNING && status != Status.PENDING)
            throw new IllegalStateException("대기 또는 실행 중인 문서 추출만 실패 처리할 수 있습니다.");
        this.status = Status.FAILED;
        this.errorMessage = message == null || message.isBlank() ? "문서 추출에 실패했습니다." : message.trim();
        this.reviewRequired = true;
        this.completedAt = LocalDateTime.now();
    }

    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

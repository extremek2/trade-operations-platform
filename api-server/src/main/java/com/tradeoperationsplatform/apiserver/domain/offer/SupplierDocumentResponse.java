package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierDocumentResponse(
        UUID documentId,
        UUID supplierId,
        SourceArtifact.SourceType sourceType,
        String sourceReference,
        String originalFileName,
        long fileSize,
        String contentType,
        String contentHash,
        String storageBackend,
        LocalDateTime receivedAt,
        UUID extractionRunId,
        ExtractionRun.Method extractionMethod,
        ExtractionRun.Status extractionStatus,
        String extractorVersion,
        String extractedText,
        BigDecimal confidence,
        Integer pageCount,
        Boolean preprocessingApplied,
        Boolean reviewRequired,
        String errorMessage,
        LocalDateTime completedAt
) {
    static SupplierDocumentResponse from(SourceArtifact artifact, ExtractionRun run) {
        return new SupplierDocumentResponse(artifact.getPublicId(), artifact.getSupplier().getPublicId(),
                artifact.getSourceType(), artifact.getSourceReference(), artifact.getOriginalFileName(),
                artifact.getFileSize(), artifact.getContentType(), artifact.getContentHash(),
                artifact.getStorageBackend(), artifact.getReceivedAt(), run.getPublicId(), run.getMethod(),
                run.getStatus(), run.getExtractorVersion(), run.getExtractedText(), run.getConfidence(),
                run.getPageCount(), run.getPreprocessingApplied(), run.getReviewRequired(),
                run.getErrorMessage(), run.getCompletedAt());
    }
}

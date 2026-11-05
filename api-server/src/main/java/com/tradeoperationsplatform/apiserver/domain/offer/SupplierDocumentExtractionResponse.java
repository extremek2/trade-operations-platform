package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SupplierDocumentExtractionResponse(UUID extractionRunId, ExtractionRun.Method method,
                                                 ExtractionRun.Status status, String extractorVersion,
                                                 BigDecimal confidence, Boolean reviewRequired,
                                                 String errorMessage, LocalDateTime createdAt,
                                                 LocalDateTime completedAt) {
    static SupplierDocumentExtractionResponse from(ExtractionRun run) {
        return new SupplierDocumentExtractionResponse(run.getPublicId(), run.getMethod(), run.getStatus(),
                run.getExtractorVersion(), run.getConfidence(), run.getReviewRequired(),
                run.getErrorMessage(), run.getCreatedAt(), run.getCompletedAt());
    }
}

package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record SupplierOfferResponse(UUID draftId, UUID sourceArtifactId, UUID extractionRunId,
                                    UUID supplierId, String supplierName, String sourceReference,
                                    SourceArtifact.SourceType sourceType, String originalFileName,
                                    String contentType, Long fileSize, String originalText,
                                    String contentHash, ExtractionRun.Method extractionMethod, String extractorVersion,
                                    String extractedText, BigDecimal extractionConfidence,
                                    String currency,
                                    SupplierOfferDraft.Status status, long version, LocalDateTime createdAt,
                                    List<Line> lines) {
    public record Line(int lineNumber, String originalName, String sourceLocation, String reviewedName,
                       String supplierSku, String quantityUnit, BigDecimal minimumQuantity,
                       BigDecimal availableQuantity, BigDecimal unitPrice, List<WholesalePriceTier> priceTiers,
                       String countryOfOrigin, String notes,
                       String extractionErrors, BigDecimal extractionConfidence,
                       SupplierOfferDraftLine.Status status, LocalDateTime reviewedAt) {}

    public static SupplierOfferResponse from(SupplierOfferDraft draft) {
        SourceArtifact source = draft.getExtractionRun().getSourceArtifact();
        return new SupplierOfferResponse(draft.getPublicId(), source.getPublicId(),
                draft.getExtractionRun().getPublicId(), draft.getSupplier().getPublicId(),
                draft.getSupplier().getName(), source.getSourceReference(), source.getSourceType(),
                source.getOriginalFileName(), source.getContentType(), source.getFileSize(), source.getOriginalText(),
                source.getContentHash().trim(), draft.getExtractionRun().getMethod(),
                draft.getExtractionRun().getExtractorVersion(), draft.getExtractionRun().getExtractedText(),
                draft.getExtractionRun().getConfidence(), draft.getCurrency(), draft.getStatus(), draft.getVersion(),
                draft.getCreatedAt(), draft.getLines().stream().map(line -> new Line(line.getLineNumber(),
                        line.getOriginalName(), line.getSourceLocation(), line.getReviewedName(),
                        line.getSupplierSku(), line.getQuantityUnit(), line.getMinimumQuantity(),
                        line.getAvailableQuantity(), line.getUnitPrice(), line.getPriceTiers().stream().map(tier -> new WholesalePriceTier(
                                tier.getMinimumQuantity(), tier.getUnitPrice())).toList(),
                        line.getCountryOfOrigin(), line.getNotes(), line.getExtractionErrors(),
                        line.getExtractionConfidence(), line.getStatus(),
                        line.getReviewedAt())).toList());
    }
}

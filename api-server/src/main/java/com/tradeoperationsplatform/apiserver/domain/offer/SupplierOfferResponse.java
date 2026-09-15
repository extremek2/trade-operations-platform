package com.tradeoperationsplatform.apiserver.domain.offer;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record SupplierOfferResponse(UUID draftId, UUID sourceArtifactId, UUID extractionRunId,
                                    UUID supplierId, String supplierName, String sourceReference,
                                    String originalText, String contentHash, String currency,
                                    SupplierOfferDraft.Status status, long version, LocalDateTime createdAt,
                                    List<Line> lines) {
    public record Line(int lineNumber, String originalName, String sourceLocation, String reviewedName,
                       String supplierSku, String quantityUnit, BigDecimal minimumQuantity,
                       BigDecimal unitPrice, String countryOfOrigin, String notes,
                       SupplierOfferDraftLine.Status status, LocalDateTime reviewedAt) {}

    public static SupplierOfferResponse from(SupplierOfferDraft draft) {
        SourceArtifact source = draft.getExtractionRun().getSourceArtifact();
        return new SupplierOfferResponse(draft.getPublicId(), source.getPublicId(),
                draft.getExtractionRun().getPublicId(), draft.getSupplier().getPublicId(),
                draft.getSupplier().getName(), source.getSourceReference(), source.getOriginalText(),
                source.getContentHash().trim(), draft.getCurrency(), draft.getStatus(), draft.getVersion(),
                draft.getCreatedAt(), draft.getLines().stream().map(line -> new Line(line.getLineNumber(),
                        line.getOriginalName(), line.getSourceLocation(), line.getReviewedName(),
                        line.getSupplierSku(), line.getQuantityUnit(), line.getMinimumQuantity(),
                        line.getUnitPrice(), line.getCountryOfOrigin(), line.getNotes(), line.getStatus(),
                        line.getReviewedAt())).toList());
    }
}

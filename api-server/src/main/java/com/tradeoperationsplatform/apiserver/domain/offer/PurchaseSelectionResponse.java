package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record PurchaseSelectionResponse(UUID selectionId, UUID draftId, UUID quoteId, String quoteNumber,
                                        SupplierQuote.Status quoteStatus, String currency, LocalDateTime selectedAt,
                                        List<Line> lines) {
    public record Line(int offerLineNumber, int quoteLineNumber, String originalName, String reviewedName,
                       UUID productId, String productName, BigDecimal desiredQuantity) {}

    public static PurchaseSelectionResponse from(PurchaseSelection selection) {
        return new PurchaseSelectionResponse(selection.getPublicId(), selection.getDraft().getPublicId(),
                selection.getQuote().getPublicId(), selection.getQuote().getQuoteNumber(),
                selection.getQuote().getStatus(), selection.getQuote().getCurrency(), selection.getSelectedAt(),
                selection.getLines().stream().map(line -> new Line(line.getOfferLine().getLineNumber(),
                        line.getQuoteLine().getLineNumber(), line.getOfferLine().getOriginalName(),
                        line.getOfferLine().getReviewedName(), line.getProduct().getPublicId(),
                        line.getProduct().getName(), line.getDesiredQuantity())).toList());
    }
}

package com.productresearch.apiserver.domain.sourcing.dto;

import com.productresearch.apiserver.domain.sourcing.entity.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public record QuoteResponse(UUID quoteId, UUID previousQuoteId, int revisionNumber,
                            UUID supplierId, String supplierName, String quoteNumber,
                            String currency, SupplierQuote.Status status, LocalDate quotedAt,
                            LocalDate validUntil, String notes, long version, List<Line> lines,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    public record Line(int lineNumber, UUID productId, String productName, String supplierSku,
                       String description, BigDecimal minimumQuantity, String quantityUnit,
                       BigDecimal unitPrice, String countryOfOrigin, Integer leadTimeDays, String notes) {}
    public static QuoteResponse from(SupplierQuote quote) {
        return new QuoteResponse(quote.getPublicId(), quote.getPreviousQuote() == null ? null : quote.getPreviousQuote().getPublicId(),
                quote.getRevisionNumber(), quote.getSupplier().getPublicId(), quote.getSupplier().getName(),
                quote.getQuoteNumber(), quote.getCurrency(), quote.getStatus(), quote.getQuotedAt(),
                quote.getValidUntil(), quote.getNotes(), quote.getVersion(), quote.getLines().stream()
                .map(line -> new Line(line.getLineNumber(), line.getProduct().getPublicId(), line.getProduct().getName(),
                        line.getSupplierSku(), line.getDescription(), line.getMinimumQuantity(), line.getQuantityUnit(),
                        line.getUnitPrice(), line.getCountryOfOrigin(), line.getLeadTimeDays(), line.getNotes())).toList(),
                quote.getCreatedAt(), quote.getUpdatedAt());
    }
}

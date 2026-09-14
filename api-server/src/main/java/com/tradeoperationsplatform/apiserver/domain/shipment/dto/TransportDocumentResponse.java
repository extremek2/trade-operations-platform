package com.tradeoperationsplatform.apiserver.domain.shipment.dto;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.TransportDocument;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransportDocumentResponse(UUID documentId, TransportDocument.Type documentType, String documentNumber,
                                        String issuerName, LocalDateTime issuedAt, boolean primary) {
    public static TransportDocumentResponse from(TransportDocument document) {
        return new TransportDocumentResponse(document.getPublicId(), document.getDocumentType(), document.getDocumentNumber(),
                document.getIssuerName(), document.getIssuedAt(), document.isPrimary());
    }
}

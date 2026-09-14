package com.tradeoperationsplatform.apiserver.domain.shipment.dto;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.TransportDocument;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

public record CreateTransportDocumentRequest(
        @NotNull TransportDocument.Type documentType, @NotBlank String documentNumber,
        String issuerName, LocalDateTime issuedAt, boolean primary
) {}

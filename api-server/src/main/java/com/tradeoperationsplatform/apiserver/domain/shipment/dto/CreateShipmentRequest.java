package com.tradeoperationsplatform.apiserver.domain.shipment.dto;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateShipmentRequest(
        @NotBlank String caseNumber, @NotNull ShipmentCase.Direction direction, @NotNull ShipmentCase.TransportMode transportMode,
        ShipmentCase.Stage currentStage, ShipmentCase.Priority priority, String shipperReference,
        String purchaseOrderNumber, String carrierName, String vesselName, String voyageNumber, String flightNumber,
        String originLocationCode, String originLocationName, String destinationLocationCode, String destinationLocationName,
        LocalDateTime etd, LocalDateTime eta, String cargoDescription, @PositiveOrZero Integer packageCount,
        @PositiveOrZero BigDecimal grossWeight, String weightUnit, @PositiveOrZero Integer containerCount
) {}

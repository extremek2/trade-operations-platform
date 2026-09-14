package com.tradeoperationsplatform.apiserver.domain.shipment.dto;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record UpdateShipmentRequest(
        @NotNull Long version, ShipmentCase.Stage currentStage, ShipmentCase.Status status,
        ShipmentCase.Priority priority, String carrierName, String vesselName, String voyageNumber,
        String flightNumber, LocalDateTime etd, LocalDateTime atd, LocalDateTime eta,
        LocalDateTime ata, String cargoDescription
) {}

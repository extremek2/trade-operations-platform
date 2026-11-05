package com.tradeoperationsplatform.apiserver.domain.shipment.dto;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record ShipmentResponse(
        UUID shipmentId, UUID ownerOrganizationId, String caseNumber, ShipmentCase.Direction direction,
        ShipmentCase.TransportMode transportMode, ShipmentCase.Stage currentStage, ShipmentCase.Status status,
        ShipmentCase.Priority priority, String shipperReference, String purchaseOrderNumber, String carrierName,
        String vesselName, String voyageNumber, String flightNumber, String originLocationCode, String originLocationName,
        String destinationLocationCode, String destinationLocationName, LocalDateTime etd, LocalDateTime atd,
        LocalDateTime eta, LocalDateTime ata, String cargoDescription, Integer packageCount, BigDecimal grossWeight,
        String weightUnit, Integer containerCount, LocalDateTime archivedAt, Long version,
        List<TransportDocumentResponse> documents
) {
    public static ShipmentResponse of(ShipmentCase shipment, List<TransportDocumentResponse> documents) {
        return new ShipmentResponse(shipment.getPublicId(), shipment.getOwnerOrganization().getPublicId(), shipment.getCaseNumber(),
                shipment.getDirection(), shipment.getTransportMode(), shipment.getCurrentStage(), shipment.getStatus(), shipment.getPriority(),
                shipment.getShipperReference(), shipment.getPurchaseOrderNumber(), shipment.getCarrierName(), shipment.getVesselName(),
                shipment.getVoyageNumber(), shipment.getFlightNumber(), shipment.getOriginLocationCode(), shipment.getOriginLocationName(),
                shipment.getDestinationLocationCode(), shipment.getDestinationLocationName(), shipment.getEtd(), shipment.getAtd(),
                shipment.getEta(), shipment.getAta(), shipment.getCargoDescription(), shipment.getPackageCount(), shipment.getGrossWeight(),
                shipment.getWeightUnit(), shipment.getContainerCount(), shipment.getArchivedAt(), shipment.getVersion(), documents);
    }
}

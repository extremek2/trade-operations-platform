package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TradeCycleResponse(
        PurchaseOrderView purchaseOrder,
        List<AllocationView> shipmentAllocations,
        List<InventoryLotView> inventoryLots,
        List<ActualCostView> actualCosts,
        CostCloseView costClose,
        List<SalesView> salesObservations,
        List<DecisionView> decisions
) {
    public record PurchaseOrderView(
            UUID purchaseOrderId, String orderNumber, PurchaseOrder.Status status,
            PurchaseOrder.PaymentStatus paymentStatus, BigDecimal paidAmount, LocalDate paidAt,
            String paymentEvidence, String currency, UUID supplierId, String supplierName,
            UUID costScenarioId, String quoteNumberSnapshot, int quoteRevisionSnapshot,
            BigDecimal expectedTotalCostKrw, LocalDate orderedAt, String notes, long version,
            LocalDateTime createdAt, List<LineView> lines
    ) {}
    public record LineView(
            UUID purchaseOrderLineId, UUID productId, String productName, String internalSku,
            BigDecimal orderedQuantity, BigDecimal shippedQuantity, BigDecimal receivedQuantity,
            String quantityUnit, BigDecimal unitPrice
    ) {}
    public record AllocationView(
            UUID allocationId, UUID shipmentId, String shipmentCaseNumber, UUID purchaseOrderLineId,
            BigDecimal allocatedQuantity, BigDecimal receivedQuantity, LocalDateTime createdAt
    ) {}
    public record InventoryLotView(
            UUID inventoryLotId, String lotNumber, UUID productId, String productName,
            UUID purchaseOrderLineId, UUID receiptId, String receiptNumber,
            BigDecimal receivedQuantity, BigDecimal sellableQuantity, long version, LocalDateTime createdAt
    ) {}
    public record ActualCostView(
            UUID actualCostItemId, ActualCostItem.Type costType, String description,
            BigDecimal amountKrw, LocalDate incurredAt, String evidenceReference
    ) {}
    public record CostCloseView(
            UUID costCloseId, BigDecimal expectedTotalKrw, BigDecimal actualTotalKrw,
            BigDecimal varianceKrw, BigDecimal receivedQuantity, BigDecimal actualUnitCostKrw,
            LocalDateTime closedAt, List<CostAllocationView> allocations
    ) {}
    public record CostAllocationView(UUID inventoryLotId, String lotNumber, BigDecimal allocatedAmountKrw) {}
    public record SalesView(
            UUID salesObservationId, UUID inventoryLotId, String lotNumber, String channel,
            LocalDate observedAt, BigDecimal soldQuantity, BigDecimal returnedQuantity,
            BigDecimal grossRevenueKrw, BigDecimal channelCostKrw, String notes
    ) {}
    public record DecisionView(
            UUID decisionId, ReorderDecision.Decision decision, String reason, LocalDateTime decidedAt
    ) {}
}

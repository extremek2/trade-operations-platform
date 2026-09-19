package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class TradeCycleRequests {
    private TradeCycleRequests() {}

    public record CreatePurchaseOrder(
            @NotEmpty List<@NotNull UUID> costScenarioIds,
            @NotBlank @Size(max = 100) String orderNumber,
            LocalDate orderedAt,
            @Size(max = 2000) String notes
    ) {}
    public record OrderAction(@NotNull Long version) {}
    public record Payment(
            @NotNull Long version,
            @NotNull PurchaseOrder.PaymentStatus paymentStatus,
            @NotNull @PositiveOrZero BigDecimal paidAmount,
            LocalDate paidAt,
            @Size(max = 1000) String evidenceReference
    ) {}
    public record AllocateShipment(
            @NotNull UUID shipmentId,
            @NotNull UUID purchaseOrderLineId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity
    ) {}
    public record CreateReceipt(
            @NotNull UUID shipmentId,
            @NotBlank @Size(max = 100) String receiptNumber,
            LocalDateTime receivedAt,
            @Size(max = 2000) String notes,
            @NotEmpty List<@Valid ReceiptEntry> lines
    ) {}
    public record ReceiptEntry(
            @NotNull UUID allocationId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank @Size(max = 100) String lotNumber
    ) {}
    public record AddActualCost(
            @NotNull ActualCostItem.Type costType,
            @NotBlank @Size(max = 500) String description,
            @NotNull @PositiveOrZero BigDecimal amountKrw,
            LocalDate incurredAt,
            @Size(max = 1000) String evidenceReference
    ) {}
    public record AddSalesObservation(
            @NotNull UUID inventoryLotId,
            @NotBlank @Size(max = 100) String channel,
            LocalDate observedAt,
            @NotNull @PositiveOrZero BigDecimal soldQuantity,
            @NotNull @PositiveOrZero BigDecimal returnedQuantity,
            @NotNull @PositiveOrZero BigDecimal grossRevenueKrw,
            @NotNull @PositiveOrZero BigDecimal channelCostKrw,
            @Size(max = 2000) String notes
    ) {}
    public record AddDecision(
            @NotNull UUID productId,
            @NotNull ReorderDecision.Decision decision,
            @NotBlank @Size(max = 2000) String reason
    ) {}
}

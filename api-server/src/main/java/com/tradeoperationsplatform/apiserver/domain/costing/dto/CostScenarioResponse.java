package com.tradeoperationsplatform.apiserver.domain.costing.dto;

import com.tradeoperationsplatform.apiserver.domain.costing.CostCalculation;
import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CostScenarioResponse(
        UUID scenarioId, UUID previousScenarioId, int revisionNumber, String scenarioName,
        UUID quoteId, String quoteNumber, int quoteRevisionNumber, String supplierName,
        UUID productId, String productName, String quoteCurrency,
        BigDecimal quotedUnitPrice, CostScenario.InputSource quotedUnitPriceSource,
        BigDecimal orderQuantity, BigDecimal excludedQuantity, BigDecimal sellableQuantity,
        BigDecimal exchangeRate, CostScenario.InputSource exchangeRateSource,
        BigDecimal taxableFreightKrw, BigDecimal customsClearanceCostKrw, BigDecimal inspectionCostKrw,
        BigDecimal warehouseCostKrw, BigDecimal domesticDeliveryCostKrw, BigDecimal otherCostKrw,
        CostScenario.InputSource logisticsCostSource,
        BigDecimal customsDutyRate, BigDecimal vatRate, CostScenario.InputSource taxRateSource,
        CostCalculation.VatTreatment vatTreatment,
        BigDecimal targetSellingPriceKrw, BigDecimal sellingFeeRate, BigDecimal variableCostPerUnitKrw,
        CostScenario.InputSource salesAssumptionSource,
        String calculationRuleVersion, CostCalculation.Status calculationStatus,
        BigDecimal productAmountQuoteCurrency, BigDecimal productAmountKrw, BigDecimal taxableValueKrw,
        BigDecimal customsDutyKrw, BigDecimal vatKrw, BigDecimal totalCashRequiredKrw,
        BigDecimal totalLandedCostKrw, BigDecimal unitLandedCostKrw,
        BigDecimal sellingFeePerUnitKrw, BigDecimal contributionMarginPerUnitKrw,
        LocalDateTime createdAt
) {
    public static CostScenarioResponse from(CostScenario scenario) {
        return new CostScenarioResponse(
                scenario.getPublicId(), scenario.getPreviousScenario() == null ? null : scenario.getPreviousScenario().getPublicId(),
                scenario.getRevisionNumber(), scenario.getScenarioName(), scenario.getQuote().getPublicId(),
                scenario.getQuote().getQuoteNumber(), scenario.getQuoteRevisionNumber(), scenario.getQuote().getSupplier().getName(),
                scenario.getProduct().getPublicId(), scenario.getProduct().getName(), scenario.getQuoteCurrency(),
                scenario.getQuotedUnitPrice(), scenario.getQuotedUnitPriceSource(), scenario.getOrderQuantity(),
                scenario.getExcludedQuantity(), scenario.getSellableQuantity(), scenario.getExchangeRate(),
                scenario.getExchangeRateSource(), scenario.getTaxableFreightKrw(), scenario.getCustomsClearanceCostKrw(),
                scenario.getInspectionCostKrw(), scenario.getWarehouseCostKrw(), scenario.getDomesticDeliveryCostKrw(),
                scenario.getOtherCostKrw(), scenario.getLogisticsCostSource(), scenario.getCustomsDutyRate(),
                scenario.getVatRate(), scenario.getTaxRateSource(), scenario.getVatTreatment(),
                scenario.getTargetSellingPriceKrw(), scenario.getSellingFeeRate(), scenario.getVariableCostPerUnitKrw(),
                scenario.getSalesAssumptionSource(), scenario.getCalculationRuleVersion(), scenario.getCalculationStatus(),
                scenario.getProductAmountQuoteCurrency(), scenario.getProductAmountKrw(), scenario.getTaxableValueKrw(),
                scenario.getCustomsDutyKrw(), scenario.getVatKrw(), scenario.getTotalCashRequiredKrw(),
                scenario.getTotalLandedCostKrw(), scenario.getUnitLandedCostKrw(),
                scenario.getSellingFeePerUnitKrw(), scenario.getContributionMarginPerUnitKrw(), scenario.getCreatedAt());
    }
}

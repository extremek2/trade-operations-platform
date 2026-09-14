package com.tradeoperationsplatform.apiserver.domain.costing.dto;

import com.tradeoperationsplatform.apiserver.domain.costing.CostCalculation;
import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public final class CostScenarioRequests {
    private CostScenarioRequests() {}

    public record Create(
            @NotNull UUID quoteId,
            @NotNull UUID productId,
            UUID previousScenarioId,
            @NotBlank @Size(max = 200) String scenarioName,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal orderQuantity,
            @NotNull @PositiveOrZero BigDecimal excludedQuantity,
            @DecimalMin(value = "0", inclusive = false) BigDecimal exchangeRate,
            CostScenario.InputSource exchangeRateSource,
            @PositiveOrZero BigDecimal taxableFreightKrw,
            @PositiveOrZero BigDecimal customsClearanceCostKrw,
            @PositiveOrZero BigDecimal inspectionCostKrw,
            @PositiveOrZero BigDecimal warehouseCostKrw,
            @PositiveOrZero BigDecimal domesticDeliveryCostKrw,
            @PositiveOrZero BigDecimal otherCostKrw,
            CostScenario.InputSource logisticsCostSource,
            @DecimalMin("0") @DecimalMax("100") BigDecimal customsDutyRate,
            @DecimalMin("0") @DecimalMax("100") BigDecimal vatRate,
            CostScenario.InputSource taxRateSource,
            @NotNull CostCalculation.VatTreatment vatTreatment,
            @PositiveOrZero BigDecimal targetSellingPriceKrw,
            @DecimalMin("0") @DecimalMax("100") BigDecimal sellingFeeRate,
            @PositiveOrZero BigDecimal variableCostPerUnitKrw,
            CostScenario.InputSource salesAssumptionSource
    ) {}
}

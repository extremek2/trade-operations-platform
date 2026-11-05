package com.tradeoperationsplatform.apiserver.domain.costing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.tradeoperationsplatform.apiserver.domain.costing.CostCalculation.*;
import static org.assertj.core.api.Assertions.*;

class CostCalculationTest {

    @Test
    void calculatesCashOutflowRecoverableVatAndContributionWithoutFloatingPointLoss() {
        Result result = calculate(inputs(VatTreatment.RECOVERABLE_EXCLUDED));

        assertThat(result.status()).isEqualTo(Status.COMPLETE);
        assertThat(result.sellableQuantity()).isEqualByComparingTo("8");
        assertThat(result.productAmountQuoteCurrency()).isEqualByComparingTo("100.0000");
        assertThat(result.productAmountKrw()).isEqualByComparingTo("130000.00");
        assertThat(result.taxableValueKrw()).isEqualByComparingTo("150000.00");
        assertThat(result.customsDutyKrw()).isEqualByComparingTo("12000.00");
        assertThat(result.vatKrw()).isEqualByComparingTo("16200.00");
        assertThat(result.totalCashRequiredKrw()).isEqualByComparingTo("191200.00");
        assertThat(result.totalLandedCostKrw()).isEqualByComparingTo("175000.00");
        assertThat(result.unitLandedCostKrw()).isEqualByComparingTo("21875.00");
        assertThat(result.sellingFeePerUnitKrw()).isEqualByComparingTo("3000.00");
        assertThat(result.contributionMarginPerUnitKrw()).isEqualByComparingTo("4125.00");
    }

    @Test
    void includedVatRemainsInLandedCost() {
        Result result = calculate(inputs(VatTreatment.CASH_COST_INCLUDED));
        assertThat(result.totalLandedCostKrw()).isEqualByComparingTo("191200.00");
        assertThat(result.unitLandedCostKrw()).isEqualByComparingTo("23900.00");
    }

    @Test
    void missingValueStaysIncompleteInsteadOfBecomingZero() {
        Inputs complete = inputs(VatTreatment.CASH_COST_INCLUDED);
        Inputs missingPrice = new Inputs(null, complete.orderQuantity(), complete.excludedQuantity(),
                complete.exchangeRate(), complete.taxableFreightKrw(), complete.customsDutyRate(), complete.vatRate(),
                complete.customsClearanceCostKrw(), complete.inspectionCostKrw(), complete.warehouseCostKrw(),
                complete.domesticDeliveryCostKrw(), complete.otherCostKrw(), complete.vatTreatment(),
                complete.targetSellingPriceKrw(), complete.sellingFeeRate(), complete.variableCostPerUnitKrw());

        Result result = calculate(missingPrice);
        assertThat(result.status()).isEqualTo(Status.INCOMPLETE);
        assertThat(result.productAmountKrw()).isNull();
        assertThat(result.totalLandedCostKrw()).isNull();
    }

    @Test
    void unitAllocationUsesHalfUpTwoDecimalRule() {
        Result result = calculate(new Inputs(bd("10"), bd("1"), bd("0.7"), bd("1"), bd("0"), bd("0"), bd("0"),
                bd("0"), bd("0"), bd("0"), bd("0"), bd("0"), VatTreatment.CASH_COST_INCLUDED,
                null, null, null));
        assertThat(result.unitLandedCostKrw()).isEqualByComparingTo("33.33");
        assertThat(result.status()).isEqualTo(Status.COST_COMPLETE);
    }

    @Test
    void rejectsZeroSellableQuantityAndInvalidRates() {
        Inputs complete = inputs(VatTreatment.CASH_COST_INCLUDED);
        assertThatThrownBy(() -> calculate(new Inputs(complete.quotedUnitPrice(), bd("10"), bd("10"),
                complete.exchangeRate(), complete.taxableFreightKrw(), complete.customsDutyRate(), complete.vatRate(),
                complete.customsClearanceCostKrw(), complete.inspectionCostKrw(), complete.warehouseCostKrw(),
                complete.domesticDeliveryCostKrw(), complete.otherCostKrw(), complete.vatTreatment(),
                complete.targetSellingPriceKrw(), complete.sellingFeeRate(), complete.variableCostPerUnitKrw())))
                .hasMessageContaining("판매가능수량");
        assertThatThrownBy(() -> calculate(new Inputs(complete.quotedUnitPrice(), complete.orderQuantity(),
                complete.excludedQuantity(), complete.exchangeRate(), complete.taxableFreightKrw(), bd("100.01"),
                complete.vatRate(), complete.customsClearanceCostKrw(), complete.inspectionCostKrw(),
                complete.warehouseCostKrw(), complete.domesticDeliveryCostKrw(), complete.otherCostKrw(),
                complete.vatTreatment(), complete.targetSellingPriceKrw(), complete.sellingFeeRate(),
                complete.variableCostPerUnitKrw()))).hasMessageContaining("관세율");
    }

    private Inputs inputs(VatTreatment vatTreatment) {
        return new Inputs(bd("10"), bd("10"), bd("2"), bd("1300"), bd("20000"), bd("8"), bd("10"),
                bd("5000"), bd("0"), bd("3000"), bd("4000"), bd("1000"), vatTreatment,
                bd("30000"), bd("10"), bd("1000"));
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}

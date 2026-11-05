package com.tradeoperationsplatform.apiserver.domain.costing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class CostCalculation {
    public static final String RULE_VERSION = "COST_V1";
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private CostCalculation() {}

    public enum VatTreatment { CASH_COST_INCLUDED, RECOVERABLE_EXCLUDED }
    public enum Status { INCOMPLETE, COST_COMPLETE, COMPLETE }

    public record Inputs(
            BigDecimal quotedUnitPrice,
            BigDecimal orderQuantity,
            BigDecimal excludedQuantity,
            BigDecimal exchangeRate,
            BigDecimal taxableFreightKrw,
            BigDecimal customsDutyRate,
            BigDecimal vatRate,
            BigDecimal customsClearanceCostKrw,
            BigDecimal inspectionCostKrw,
            BigDecimal warehouseCostKrw,
            BigDecimal domesticDeliveryCostKrw,
            BigDecimal otherCostKrw,
            VatTreatment vatTreatment,
            BigDecimal targetSellingPriceKrw,
            BigDecimal sellingFeeRate,
            BigDecimal variableCostPerUnitKrw
    ) {}

    public record Result(
            Status status,
            BigDecimal sellableQuantity,
            BigDecimal productAmountQuoteCurrency,
            BigDecimal productAmountKrw,
            BigDecimal taxableValueKrw,
            BigDecimal customsDutyKrw,
            BigDecimal vatKrw,
            BigDecimal totalCashRequiredKrw,
            BigDecimal totalLandedCostKrw,
            BigDecimal unitLandedCostKrw,
            BigDecimal sellingFeePerUnitKrw,
            BigDecimal contributionMarginPerUnitKrw
    ) {}

    public static Result calculate(Inputs inputs) {
        if (inputs == null) throw new IllegalArgumentException("원가 계산 입력은 필수입니다.");
        positive(inputs.orderQuantity(), "주문수량");
        nonNegative(inputs.excludedQuantity(), "판매 제외수량");
        if (inputs.excludedQuantity().compareTo(inputs.orderQuantity()) >= 0) {
            throw new IllegalArgumentException("판매가능수량은 0보다 커야 합니다.");
        }
        BigDecimal sellableQuantity = inputs.orderQuantity().subtract(inputs.excludedQuantity());
        nullableNonNegative(inputs.quotedUnitPrice(), "견적 단가");
        nullablePositive(inputs.exchangeRate(), "환율");
        nullableRate(inputs.customsDutyRate(), "관세율");
        nullableRate(inputs.vatRate(), "부가세율");
        nullableRate(inputs.sellingFeeRate(), "판매수수료율");
        for (NamedAmount amount : List.of(
                new NamedAmount("과세대상 운임·보험료", inputs.taxableFreightKrw()),
                new NamedAmount("통관비", inputs.customsClearanceCostKrw()),
                new NamedAmount("검사비", inputs.inspectionCostKrw()),
                new NamedAmount("창고비", inputs.warehouseCostKrw()),
                new NamedAmount("국내배송비", inputs.domesticDeliveryCostKrw()),
                new NamedAmount("기타비용", inputs.otherCostKrw()),
                new NamedAmount("목표 판매가", inputs.targetSellingPriceKrw()),
                new NamedAmount("건별 변동비", inputs.variableCostPerUnitKrw()))) {
            nullableNonNegative(amount.value(), amount.name());
        }
        if (inputs.vatTreatment() == null) throw new IllegalArgumentException("부가세 관점은 필수입니다.");

        if (!hasCostInputs(inputs)) {
            return new Result(Status.INCOMPLETE, sellableQuantity, null, null, null, null, null,
                    null, null, null, null, null);
        }

        BigDecimal productAmountQuoteCurrency = inputs.quotedUnitPrice().multiply(inputs.orderQuantity())
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal productAmountKrw = money(productAmountQuoteCurrency.multiply(inputs.exchangeRate()));
        BigDecimal taxableValueKrw = money(productAmountKrw.add(inputs.taxableFreightKrw()));
        BigDecimal customsDutyKrw = percentage(taxableValueKrw, inputs.customsDutyRate());
        BigDecimal vatKrw = percentage(taxableValueKrw.add(customsDutyKrw), inputs.vatRate());
        BigDecimal nonTaxCosts = inputs.customsClearanceCostKrw().add(inputs.inspectionCostKrw())
                .add(inputs.warehouseCostKrw()).add(inputs.domesticDeliveryCostKrw()).add(inputs.otherCostKrw());
        BigDecimal totalCashRequiredKrw = money(taxableValueKrw.add(customsDutyKrw).add(vatKrw).add(nonTaxCosts));
        BigDecimal totalLandedCostKrw = inputs.vatTreatment() == VatTreatment.RECOVERABLE_EXCLUDED
                ? money(totalCashRequiredKrw.subtract(vatKrw)) : totalCashRequiredKrw;
        BigDecimal unitLandedCostKrw = totalLandedCostKrw.divide(sellableQuantity, 2, RoundingMode.HALF_UP);

        if (!hasSalesInputs(inputs)) {
            return new Result(Status.COST_COMPLETE, sellableQuantity, productAmountQuoteCurrency, productAmountKrw,
                    taxableValueKrw, customsDutyKrw, vatKrw, totalCashRequiredKrw, totalLandedCostKrw,
                    unitLandedCostKrw, null, null);
        }
        BigDecimal sellingFeePerUnitKrw = percentage(inputs.targetSellingPriceKrw(), inputs.sellingFeeRate());
        BigDecimal contributionMarginPerUnitKrw = money(inputs.targetSellingPriceKrw()
                .subtract(unitLandedCostKrw).subtract(sellingFeePerUnitKrw).subtract(inputs.variableCostPerUnitKrw()));
        return new Result(Status.COMPLETE, sellableQuantity, productAmountQuoteCurrency, productAmountKrw,
                taxableValueKrw, customsDutyKrw, vatKrw, totalCashRequiredKrw, totalLandedCostKrw,
                unitLandedCostKrw, sellingFeePerUnitKrw, contributionMarginPerUnitKrw);
    }

    private static boolean hasCostInputs(Inputs inputs) {
        return inputs.quotedUnitPrice() != null && inputs.exchangeRate() != null
                && inputs.taxableFreightKrw() != null && inputs.customsDutyRate() != null && inputs.vatRate() != null
                && inputs.customsClearanceCostKrw() != null && inputs.inspectionCostKrw() != null
                && inputs.warehouseCostKrw() != null && inputs.domesticDeliveryCostKrw() != null
                && inputs.otherCostKrw() != null;
    }

    private static boolean hasSalesInputs(Inputs inputs) {
        return inputs.targetSellingPriceKrw() != null && inputs.sellingFeeRate() != null
                && inputs.variableCostPerUnitKrw() != null;
    }

    private static BigDecimal percentage(BigDecimal amount, BigDecimal rate) {
        return money(amount.multiply(rate).divide(ONE_HUNDRED, 12, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }

    private static void positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + "은 0보다 커야 합니다.");
    }
    private static void nullablePositive(BigDecimal value, String name) {
        if (value != null && value.signum() <= 0) throw new IllegalArgumentException(name + "은 0보다 커야 합니다.");
    }
    private static void nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + "은 0 이상이어야 합니다.");
    }
    private static void nullableNonNegative(BigDecimal value, String name) {
        if (value != null && value.signum() < 0) throw new IllegalArgumentException(name + "은 0 이상이어야 합니다.");
    }
    private static void nullableRate(BigDecimal value, String name) {
        if (value != null && (value.signum() < 0 || value.compareTo(ONE_HUNDRED) > 0)) {
            throw new IllegalArgumentException(name + "은 0 이상 100 이하여야 합니다.");
        }
    }
    private record NamedAmount(String name, BigDecimal value) {}
}

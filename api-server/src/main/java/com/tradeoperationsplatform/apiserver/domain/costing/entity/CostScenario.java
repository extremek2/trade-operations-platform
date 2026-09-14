package com.tradeoperationsplatform.apiserver.domain.costing.entity;

import com.tradeoperationsplatform.apiserver.domain.costing.CostCalculation;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuoteLine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "cost_scenario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CostScenario {
    public enum InputSource { SUPPLIER_QUOTE, USER_ASSUMPTION, MARKET_REFERENCE, PROFESSIONAL_CONFIRMATION }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id") private SupplierQuote quote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "previous_scenario_id", unique = true) private CostScenario previousScenario;
    @Column(name = "revision_number", nullable = false) private int revisionNumber;
    @Column(name = "scenario_name", nullable = false) private String scenarioName;

    @Column(name = "quote_revision_number", nullable = false) private int quoteRevisionNumber;
    @Column(name = "quote_currency", nullable = false, length = 3) private String quoteCurrency;
    @Column(name = "quoted_unit_price", precision = 19, scale = 4) private BigDecimal quotedUnitPrice;
    @Enumerated(EnumType.STRING) @Column(name = "quoted_unit_price_source") private InputSource quotedUnitPriceSource;
    @Column(name = "order_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal orderQuantity;
    @Column(name = "excluded_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal excludedQuantity;
    @Column(name = "exchange_rate", precision = 19, scale = 6) private BigDecimal exchangeRate;
    @Enumerated(EnumType.STRING) @Column(name = "exchange_rate_source") private InputSource exchangeRateSource;
    @Column(name = "taxable_freight_krw", precision = 19, scale = 2) private BigDecimal taxableFreightKrw;
    @Column(name = "customs_clearance_cost_krw", precision = 19, scale = 2) private BigDecimal customsClearanceCostKrw;
    @Column(name = "inspection_cost_krw", precision = 19, scale = 2) private BigDecimal inspectionCostKrw;
    @Column(name = "warehouse_cost_krw", precision = 19, scale = 2) private BigDecimal warehouseCostKrw;
    @Column(name = "domestic_delivery_cost_krw", precision = 19, scale = 2) private BigDecimal domesticDeliveryCostKrw;
    @Column(name = "other_cost_krw", precision = 19, scale = 2) private BigDecimal otherCostKrw;
    @Enumerated(EnumType.STRING) @Column(name = "logistics_cost_source") private InputSource logisticsCostSource;
    @Column(name = "customs_duty_rate", precision = 9, scale = 6) private BigDecimal customsDutyRate;
    @Column(name = "vat_rate", precision = 9, scale = 6) private BigDecimal vatRate;
    @Enumerated(EnumType.STRING) @Column(name = "tax_rate_source") private InputSource taxRateSource;
    @Enumerated(EnumType.STRING) @Column(name = "vat_treatment", nullable = false) private CostCalculation.VatTreatment vatTreatment;
    @Column(name = "target_selling_price_krw", precision = 19, scale = 2) private BigDecimal targetSellingPriceKrw;
    @Column(name = "selling_fee_rate", precision = 9, scale = 6) private BigDecimal sellingFeeRate;
    @Column(name = "variable_cost_per_unit_krw", precision = 19, scale = 2) private BigDecimal variableCostPerUnitKrw;
    @Enumerated(EnumType.STRING) @Column(name = "sales_assumption_source") private InputSource salesAssumptionSource;

    @Column(name = "calculation_rule_version", nullable = false) private String calculationRuleVersion;
    @Enumerated(EnumType.STRING) @Column(name = "calculation_status", nullable = false) private CostCalculation.Status calculationStatus;
    @Column(name = "sellable_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal sellableQuantity;
    @Column(name = "product_amount_quote_currency", precision = 19, scale = 4) private BigDecimal productAmountQuoteCurrency;
    @Column(name = "product_amount_krw", precision = 19, scale = 2) private BigDecimal productAmountKrw;
    @Column(name = "taxable_value_krw", precision = 19, scale = 2) private BigDecimal taxableValueKrw;
    @Column(name = "customs_duty_krw", precision = 19, scale = 2) private BigDecimal customsDutyKrw;
    @Column(name = "vat_krw", precision = 19, scale = 2) private BigDecimal vatKrw;
    @Column(name = "total_cash_required_krw", precision = 19, scale = 2) private BigDecimal totalCashRequiredKrw;
    @Column(name = "total_landed_cost_krw", precision = 19, scale = 2) private BigDecimal totalLandedCostKrw;
    @Column(name = "unit_landed_cost_krw", precision = 19, scale = 2) private BigDecimal unitLandedCostKrw;
    @Column(name = "selling_fee_per_unit_krw", precision = 19, scale = 2) private BigDecimal sellingFeePerUnitKrw;
    @Column(name = "contribution_margin_per_unit_krw", precision = 19, scale = 2) private BigDecimal contributionMarginPerUnitKrw;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public CostScenario(Organization organization, AppUser createdBy, SupplierQuote quote, SupplierQuoteLine quoteLine,
                        CostScenario previousScenario, String scenarioName, Assumptions assumptions) {
        if (organization == null || createdBy == null || quote == null || quoteLine == null || assumptions == null) {
            throw new IllegalArgumentException("원가 시나리오의 필수 연결정보가 없습니다.");
        }
        if (!Objects.equals(quote.getOrganization().getId(), organization.getId()) || quoteLine.getQuote() != quote) {
            throw new IllegalArgumentException("같은 조직의 견적 품목만 원가 시나리오에 연결할 수 있습니다.");
        }
        if (previousScenario != null && (!Objects.equals(previousScenario.getOrganization().getId(), organization.getId())
                || !Objects.equals(previousScenario.getProduct().getId(), quoteLine.getProduct().getId()))) {
            throw new IllegalArgumentException("같은 조직과 상품의 시나리오만 개정할 수 있습니다.");
        }
        requireSource(assumptions.exchangeRate(), assumptions.exchangeRateSource(), "환율");
        requireGroupSource(anyValue(assumptions.taxableFreightKrw(), assumptions.customsClearanceCostKrw(),
                assumptions.inspectionCostKrw(), assumptions.warehouseCostKrw(), assumptions.domesticDeliveryCostKrw(),
                assumptions.otherCostKrw()), assumptions.logisticsCostSource(), "물류·부대비용");
        requireGroupSource(anyValue(assumptions.customsDutyRate(), assumptions.vatRate()), assumptions.taxRateSource(), "세율");
        requireGroupSource(anyValue(assumptions.targetSellingPriceKrw(), assumptions.sellingFeeRate(),
                assumptions.variableCostPerUnitKrw()), assumptions.salesAssumptionSource(), "판매 가정");

        CostCalculation.Result result = CostCalculation.calculate(new CostCalculation.Inputs(
                quoteLine.getUnitPrice(), assumptions.orderQuantity(), assumptions.excludedQuantity(),
                assumptions.exchangeRate(), assumptions.taxableFreightKrw(), assumptions.customsDutyRate(),
                assumptions.vatRate(), assumptions.customsClearanceCostKrw(), assumptions.inspectionCostKrw(),
                assumptions.warehouseCostKrw(), assumptions.domesticDeliveryCostKrw(), assumptions.otherCostKrw(),
                assumptions.vatTreatment(), assumptions.targetSellingPriceKrw(), assumptions.sellingFeeRate(),
                assumptions.variableCostPerUnitKrw()));

        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.createdBy = createdBy;
        this.quote = quote;
        this.product = quoteLine.getProduct();
        this.previousScenario = previousScenario;
        this.revisionNumber = previousScenario == null ? 1 : previousScenario.getRevisionNumber() + 1;
        this.scenarioName = required(scenarioName, "시나리오명");
        this.quoteRevisionNumber = quote.getRevisionNumber();
        this.quoteCurrency = quote.getCurrency();
        this.quotedUnitPrice = quoteLine.getUnitPrice();
        this.quotedUnitPriceSource = quoteLine.getUnitPrice() == null ? null : InputSource.SUPPLIER_QUOTE;
        this.orderQuantity = assumptions.orderQuantity();
        this.excludedQuantity = assumptions.excludedQuantity();
        this.exchangeRate = assumptions.exchangeRate();
        this.exchangeRateSource = assumptions.exchangeRateSource();
        this.taxableFreightKrw = assumptions.taxableFreightKrw();
        this.customsClearanceCostKrw = assumptions.customsClearanceCostKrw();
        this.inspectionCostKrw = assumptions.inspectionCostKrw();
        this.warehouseCostKrw = assumptions.warehouseCostKrw();
        this.domesticDeliveryCostKrw = assumptions.domesticDeliveryCostKrw();
        this.otherCostKrw = assumptions.otherCostKrw();
        this.logisticsCostSource = assumptions.logisticsCostSource();
        this.customsDutyRate = assumptions.customsDutyRate();
        this.vatRate = assumptions.vatRate();
        this.taxRateSource = assumptions.taxRateSource();
        this.vatTreatment = assumptions.vatTreatment();
        this.targetSellingPriceKrw = assumptions.targetSellingPriceKrw();
        this.sellingFeeRate = assumptions.sellingFeeRate();
        this.variableCostPerUnitKrw = assumptions.variableCostPerUnitKrw();
        this.salesAssumptionSource = assumptions.salesAssumptionSource();
        this.calculationRuleVersion = CostCalculation.RULE_VERSION;
        this.calculationStatus = result.status();
        this.sellableQuantity = result.sellableQuantity();
        this.productAmountQuoteCurrency = result.productAmountQuoteCurrency();
        this.productAmountKrw = result.productAmountKrw();
        this.taxableValueKrw = result.taxableValueKrw();
        this.customsDutyKrw = result.customsDutyKrw();
        this.vatKrw = result.vatKrw();
        this.totalCashRequiredKrw = result.totalCashRequiredKrw();
        this.totalLandedCostKrw = result.totalLandedCostKrw();
        this.unitLandedCostKrw = result.unitLandedCostKrw();
        this.sellingFeePerUnitKrw = result.sellingFeePerUnitKrw();
        this.contributionMarginPerUnitKrw = result.contributionMarginPerUnitKrw();
    }

    public record Assumptions(
            BigDecimal orderQuantity, BigDecimal excludedQuantity,
            BigDecimal exchangeRate, InputSource exchangeRateSource,
            BigDecimal taxableFreightKrw, BigDecimal customsClearanceCostKrw, BigDecimal inspectionCostKrw,
            BigDecimal warehouseCostKrw, BigDecimal domesticDeliveryCostKrw, BigDecimal otherCostKrw,
            InputSource logisticsCostSource, BigDecimal customsDutyRate, BigDecimal vatRate,
            InputSource taxRateSource, CostCalculation.VatTreatment vatTreatment,
            BigDecimal targetSellingPriceKrw, BigDecimal sellingFeeRate, BigDecimal variableCostPerUnitKrw,
            InputSource salesAssumptionSource
    ) {}

    @PrePersist void create() { createdAt = LocalDateTime.now(); }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "은 필수입니다.");
        return value.trim();
    }
    private static void requireSource(BigDecimal value, InputSource source, String field) {
        if ((value == null) != (source == null)) throw new IllegalArgumentException(field + " 값과 출처는 함께 입력해야 합니다.");
    }
    private static void requireGroupSource(boolean anyValue, InputSource source, String field) {
        if (anyValue != (source != null)) throw new IllegalArgumentException(field + " 값과 출처는 함께 입력해야 합니다.");
    }
    private static boolean anyValue(BigDecimal... values) {
        for (BigDecimal value : values) if (value != null) return true;
        return false;
    }
}

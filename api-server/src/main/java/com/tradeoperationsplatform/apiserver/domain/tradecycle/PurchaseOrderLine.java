package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrderLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id") private PurchaseOrder purchaseOrder;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cost_scenario_id", unique = true) private CostScenario costScenario;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "product_name_snapshot", nullable = false) private String productNameSnapshot;
    @Column(name = "internal_sku_snapshot") private String internalSkuSnapshot;
    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal orderedQuantity;
    @Column(name = "quantity_unit", nullable = false) private String quantityUnit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;

    public PurchaseOrderLine(PurchaseOrder order, CostScenario scenario, int lineNumber, String quantityUnit) {
        if (lineNumber < 1) throw new IllegalArgumentException("발주 행 번호는 양수여야 합니다.");
        this.publicId = UUID.randomUUID();
        this.purchaseOrder = order;
        this.costScenario = scenario;
        this.product = scenario.getProduct();
        this.lineNumber = lineNumber;
        this.productNameSnapshot = product.getName();
        this.internalSkuSnapshot = product.getInternalSku();
        this.orderedQuantity = scenario.getOrderQuantity();
        this.quantityUnit = quantityUnit == null || quantityUnit.isBlank() ? "EA" : quantityUnit.trim();
        this.unitPrice = scenario.getQuotedUnitPrice();
    }
}

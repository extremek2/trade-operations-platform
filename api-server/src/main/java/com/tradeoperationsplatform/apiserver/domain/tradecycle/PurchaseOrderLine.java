package com.tradeoperationsplatform.apiserver.domain.tradecycle;

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
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "product_name_snapshot", nullable = false) private String productNameSnapshot;
    @Column(name = "internal_sku_snapshot") private String internalSkuSnapshot;
    @Column(name = "ordered_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal orderedQuantity;
    @Column(name = "quantity_unit", nullable = false) private String quantityUnit;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;

    public PurchaseOrderLine(PurchaseOrder order, Product product, BigDecimal quantity, String quantityUnit, BigDecimal unitPrice) {
        this.publicId = UUID.randomUUID();
        this.purchaseOrder = order;
        this.product = product;
        this.lineNumber = 1;
        this.productNameSnapshot = product.getName();
        this.internalSkuSnapshot = product.getInternalSku();
        this.orderedQuantity = quantity;
        this.quantityUnit = quantityUnit == null || quantityUnit.isBlank() ? "EA" : quantityUnit.trim();
        this.unitPrice = unitPrice;
    }
}

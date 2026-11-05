package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inventory_lot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryLot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_line_id") private PurchaseOrderLine purchaseOrderLine;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private Receipt receipt;
    @Column(name = "lot_number", nullable = false) private String lotNumber;
    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal receivedQuantity;
    @Column(name = "sellable_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal sellableQuantity;
    @Version private long version;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public InventoryLot(Organization organization, PurchaseOrderLine line, Receipt receipt,
                        String lotNumber, BigDecimal receivedQuantity) {
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.product = line.getProduct();
        this.purchaseOrderLine = line;
        this.receipt = receipt;
        if (lotNumber == null || lotNumber.isBlank()) throw new IllegalArgumentException("로트번호는 필수입니다.");
        this.lotNumber = lotNumber.trim();
        this.receivedQuantity = receivedQuantity;
        this.sellableQuantity = receivedQuantity;
    }

    public void observeSale(BigDecimal sold, BigDecimal returned) {
        BigDecimal next = sellableQuantity.subtract(sold).add(returned);
        if (next.signum() < 0 || next.compareTo(receivedQuantity) > 0) {
            throw new IllegalStateException("판매·반품 수량이 로트의 판매 가능 수량 범위를 벗어납니다.");
        }
        sellableQuantity = next;
    }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

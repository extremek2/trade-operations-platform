package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuoteLine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "purchase_selection_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseSelectionLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_selection_id") private PurchaseSelection selection;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_offer_draft_line_id") private SupplierOfferDraftLine offerLine;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_quote_line_id") private SupplierQuoteLine quoteLine;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "desired_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal desiredQuantity;

    public PurchaseSelectionLine(PurchaseSelection selection, SupplierOfferDraftLine offerLine,
                                 SupplierQuoteLine quoteLine, Product product, int lineNumber, BigDecimal desiredQuantity) {
        if (lineNumber < 1) throw new IllegalArgumentException("선택 행 번호는 양수여야 합니다.");
        if (desiredQuantity == null || desiredQuantity.signum() <= 0) throw new IllegalArgumentException("희망 수량은 양수여야 합니다.");
        this.selection = Objects.requireNonNull(selection);
        this.offerLine = Objects.requireNonNull(offerLine);
        this.quoteLine = Objects.requireNonNull(quoteLine);
        this.product = Objects.requireNonNull(product);
        this.lineNumber = lineNumber;
        this.desiredQuantity = desiredQuantity;
    }
}

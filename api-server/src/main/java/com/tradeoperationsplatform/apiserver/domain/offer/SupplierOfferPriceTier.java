package com.tradeoperationsplatform.apiserver.domain.offer;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "supplier_offer_price_tier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierOfferPriceTier {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_offer_draft_line_id") private SupplierOfferDraftLine offerLine;
    @Column(name = "minimum_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal minimumQuantity;
    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4) private BigDecimal unitPrice;

    public SupplierOfferPriceTier(SupplierOfferDraftLine offerLine, BigDecimal minimumQuantity, BigDecimal unitPrice) {
        if (minimumQuantity == null || minimumQuantity.signum() <= 0)
            throw new IllegalArgumentException("구간가 최소수량은 양수여야 합니다.");
        if (unitPrice == null || unitPrice.signum() < 0)
            throw new IllegalArgumentException("구간가 단가는 음수일 수 없습니다.");
        this.offerLine = Objects.requireNonNull(offerLine);
        this.minimumQuantity = minimumQuantity;
        this.unitPrice = unitPrice;
    }
}

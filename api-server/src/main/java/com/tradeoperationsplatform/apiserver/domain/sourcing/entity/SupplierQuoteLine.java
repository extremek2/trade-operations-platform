package com.tradeoperationsplatform.apiserver.domain.sourcing.entity;

import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Locale;

@Entity
@Table(name = "supplier_quote_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierQuoteLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "quote_id") private SupplierQuote quote;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "supplier_sku") private String supplierSku;
    @Column(nullable = false) private String description;
    @Column(name = "minimum_quantity", precision = 19, scale = 6) private BigDecimal minimumQuantity;
    @Column(name = "quantity_unit") private String quantityUnit;
    @Column(name = "unit_price", precision = 19, scale = 4) private BigDecimal unitPrice;
    @Column(name = "country_of_origin", length = 2) private String countryOfOrigin;
    @Column(name = "lead_time_days") private Integer leadTimeDays;
    private String notes;

    public SupplierQuoteLine(SupplierQuote quote, Product product, int lineNumber, String supplierSku,
                             String description, BigDecimal minimumQuantity, String quantityUnit,
                             BigDecimal unitPrice, String countryOfOrigin, Integer leadTimeDays, String notes) {
        if (lineNumber < 1) throw new IllegalArgumentException("견적 행 번호는 1 이상이어야 합니다.");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("견적 품목 설명은 필수입니다.");
        if (minimumQuantity != null && minimumQuantity.signum() <= 0) throw new IllegalArgumentException("최소수량은 0보다 커야 합니다.");
        if (minimumQuantity != null && (quantityUnit == null || quantityUnit.isBlank())) throw new IllegalArgumentException("수량 단위는 필수입니다.");
        if (unitPrice != null && unitPrice.signum() < 0) throw new IllegalArgumentException("단가는 음수일 수 없습니다.");
        if (leadTimeDays != null && leadTimeDays < 0) throw new IllegalArgumentException("납기는 음수일 수 없습니다.");
        this.quote = quote;
        this.product = product;
        this.lineNumber = lineNumber;
        this.supplierSku = blankToNull(supplierSku);
        this.description = description.trim();
        this.minimumQuantity = minimumQuantity;
        this.quantityUnit = blankToNull(quantityUnit);
        this.unitPrice = unitPrice;
        this.countryOfOrigin = normalizeCountry(countryOfOrigin);
        this.leadTimeDays = leadTimeDays;
        this.notes = blankToNull(notes);
    }

    private static String normalizeCountry(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        if (normalized.length() != 2) throw new IllegalArgumentException("원산지는 ISO 2자리 코드여야 합니다.");
        return normalized.toUpperCase(Locale.ROOT);
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

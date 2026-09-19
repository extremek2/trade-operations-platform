package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "supplier_offer_draft_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierOfferDraftLine {
    public enum Status { PENDING, CONFIRMED, EXCLUDED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_offer_draft_id") private SupplierOfferDraft draft;
    @Column(name = "line_number", nullable = false) private int lineNumber;
    @Column(name = "original_name", nullable = false) private String originalName;
    @Column(name = "source_location") private String sourceLocation;
    @Column(name = "reviewed_name") private String reviewedName;
    @Column(name = "supplier_sku") private String supplierSku;
    @Column(name = "quantity_unit") private String quantityUnit;
    @Column(name = "minimum_quantity", precision = 19, scale = 6) private BigDecimal minimumQuantity;
    @Column(name = "unit_price", precision = 19, scale = 4) private BigDecimal unitPrice;
    @Column(name = "country_of_origin", length = 2) private String countryOfOrigin;
    @Column(name = "notes") private String notes;
    @Column(name = "extraction_errors") private String extractionErrors;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private AppUser reviewedBy;

    public SupplierOfferDraftLine(SupplierOfferDraft draft, int lineNumber, String originalName, String sourceLocation) {
        if (lineNumber < 1) throw new IllegalArgumentException("행 번호는 1 이상이어야 합니다.");
        if (originalName == null || originalName.isBlank()) throw new IllegalArgumentException("원본 상품명은 필수입니다.");
        this.draft = draft;
        this.lineNumber = lineNumber;
        this.originalName = originalName.trim();
        this.sourceLocation = blankToNull(sourceLocation);
        this.status = Status.PENDING;
    }

    public void applyExtraction(String reviewedName, String supplierSku, String quantityUnit,
                                BigDecimal minimumQuantity, BigDecimal unitPrice, String countryOfOrigin,
                                String notes, List<String> errors) {
        ensureEditable();
        this.reviewedName = blankToNull(reviewedName);
        this.supplierSku = blankToNull(supplierSku);
        this.quantityUnit = blankToNull(quantityUnit);
        this.minimumQuantity = minimumQuantity;
        this.unitPrice = unitPrice;
        String origin = blankToNull(countryOfOrigin);
        this.countryOfOrigin = origin == null ? null : origin.toUpperCase(Locale.ROOT);
        this.notes = blankToNull(notes);
        this.extractionErrors = errors == null || errors.isEmpty() ? null : String.join("\n", errors);
    }

    public void review(String reviewedName, String supplierSku, String quantityUnit,
                       BigDecimal minimumQuantity, BigDecimal unitPrice, String countryOfOrigin, String notes) {
        ensureEditable();
        if (reviewedName == null || reviewedName.isBlank()) throw new IllegalArgumentException("확인 상품명은 필수입니다.");
        if (minimumQuantity != null && minimumQuantity.signum() <= 0) throw new IllegalArgumentException("최소수량은 양수여야 합니다.");
        if (minimumQuantity != null && blankToNull(quantityUnit) == null) throw new IllegalArgumentException("최소수량의 단위가 필요합니다.");
        if (unitPrice != null && unitPrice.signum() < 0) throw new IllegalArgumentException("단가는 음수일 수 없습니다.");
        String origin = blankToNull(countryOfOrigin);
        if (origin != null && !origin.matches("[A-Za-z]{2}")) throw new IllegalArgumentException("원산지는 ISO 2자리 코드여야 합니다.");
        this.reviewedName = reviewedName.trim();
        this.supplierSku = blankToNull(supplierSku);
        this.quantityUnit = blankToNull(quantityUnit);
        this.minimumQuantity = minimumQuantity;
        this.unitPrice = unitPrice;
        this.countryOfOrigin = origin == null ? null : origin.toUpperCase(Locale.ROOT);
        this.notes = blankToNull(notes);
        this.extractionErrors = null;
        this.status = Status.CONFIRMED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void exclude() {
        ensureEditable();
        this.status = Status.EXCLUDED;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reviewedBy(AppUser actor) { this.reviewedBy = actor; }

    private void ensureEditable() {
        if (draft.getStatus() != SupplierOfferDraft.Status.REVIEW_REQUIRED)
            throw new IllegalStateException("추출 확인 후에는 행을 수정할 수 없습니다.");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

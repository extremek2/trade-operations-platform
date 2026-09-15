package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "supplier_offer_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierOfferDraft {
    public enum Status { REVIEW_REQUIRED, CONFIRMED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private BusinessPartner supplier;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "extraction_run_id") private ExtractionRun extractionRun;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL)
    @OrderBy("lineNumber ASC") private List<SupplierOfferDraftLine> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public SupplierOfferDraft(Organization organization, BusinessPartner supplier, ExtractionRun extractionRun,
                              AppUser createdBy, String currency) {
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}"))
            throw new IllegalArgumentException("통화는 ISO 3자리 코드여야 합니다.");
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.supplier = supplier;
        this.extractionRun = extractionRun;
        this.createdBy = createdBy;
        this.currency = currency.trim().toUpperCase(Locale.ROOT);
        this.status = Status.REVIEW_REQUIRED;
    }

    public void addLine(SupplierOfferDraftLine line) {
        if (status != Status.REVIEW_REQUIRED) throw new IllegalStateException("확인된 초안에는 행을 추가할 수 없습니다.");
        lines.add(Objects.requireNonNull(line));
    }

    public void confirm() {
        if (status != Status.REVIEW_REQUIRED || lines.isEmpty() ||
                lines.stream().anyMatch(line -> line.getStatus() == SupplierOfferDraftLine.Status.PENDING))
            throw new IllegalStateException("모든 행을 확인하거나 제외한 뒤 추출을 확인할 수 있습니다.");
        status = Status.CONFIRMED;
    }

    public void touchReview() {
        if (status != Status.REVIEW_REQUIRED) throw new IllegalStateException("확인된 초안은 수정할 수 없습니다.");
        updatedAt = LocalDateTime.now();
    }

    public void recordSelection() {
        if (status != Status.CONFIRMED) throw new IllegalStateException("추출 확인 후에만 품목을 선택할 수 있습니다.");
        updatedAt = LocalDateTime.now();
    }

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

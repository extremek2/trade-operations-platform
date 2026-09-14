package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseOrder {
    public enum Status { DRAFT, APPROVED, PARTIALLY_SHIPPED, SHIPPED, COMPLETED, CANCELLED }
    public enum PaymentStatus { UNPAID, PARTIALLY_PAID, PAID }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private BusinessPartner supplier;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cost_scenario_id") private CostScenario costScenario;
    @Column(name = "order_number", nullable = false) private String orderNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false) private PaymentStatus paymentStatus;
    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 4) private BigDecimal paidAmount;
    @Column(name = "paid_at") private LocalDate paidAt;
    @Column(name = "payment_evidence") private String paymentEvidence;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "supplier_name_snapshot", nullable = false) private String supplierNameSnapshot;
    @Column(name = "quote_number_snapshot") private String quoteNumberSnapshot;
    @Column(name = "quote_revision_snapshot", nullable = false) private int quoteRevisionSnapshot;
    @Column(name = "expected_total_cost_krw", nullable = false, precision = 19, scale = 2) private BigDecimal expectedTotalCostKrw;
    @Column(name = "ordered_at", nullable = false) private LocalDate orderedAt;
    @Column(name = "approved_at") private LocalDateTime approvedAt;
    @Column(name = "cancelled_at") private LocalDateTime cancelledAt;
    private String notes;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("lineNumber ASC") private List<PurchaseOrderLine> lines = new ArrayList<>();

    public PurchaseOrder(Organization organization, AppUser creator, CostScenario scenario,
                         String orderNumber, LocalDate orderedAt, String notes) {
        if (scenario.getTotalLandedCostKrw() == null || scenario.getQuotedUnitPrice() == null) {
            throw new IllegalArgumentException("완성된 예상 원가 시나리오만 발주로 전환할 수 있습니다.");
        }
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.createdBy = creator;
        this.costScenario = scenario;
        this.supplier = scenario.getQuote().getSupplier();
        this.orderNumber = required(orderNumber, "발주번호");
        this.status = Status.DRAFT;
        this.paymentStatus = PaymentStatus.UNPAID;
        this.paidAmount = BigDecimal.ZERO;
        this.currency = scenario.getQuoteCurrency();
        this.supplierNameSnapshot = supplier.getName();
        this.quoteNumberSnapshot = scenario.getQuote().getQuoteNumber();
        this.quoteRevisionSnapshot = scenario.getQuoteRevisionNumber();
        this.expectedTotalCostKrw = scenario.getTotalLandedCostKrw();
        this.orderedAt = orderedAt == null ? LocalDate.now() : orderedAt;
        this.notes = trimToNull(notes);
    }

    public void addLine(PurchaseOrderLine line) { lines.add(line); }

    public void approve() {
        if (status != Status.DRAFT) throw new IllegalStateException("초안 발주만 승인할 수 있습니다.");
        status = Status.APPROVED;
        approvedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (status != Status.DRAFT && status != Status.APPROVED) {
            throw new IllegalStateException("선적이 시작된 발주는 취소할 수 없습니다.");
        }
        status = Status.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    public void recordPayment(PaymentStatus target, BigDecimal amount, LocalDate date, String evidence) {
        if (status == Status.CANCELLED) throw new IllegalStateException("취소된 발주에는 결제를 기록할 수 없습니다.");
        if (target == null || amount == null || amount.signum() < 0) throw new IllegalArgumentException("결제 상태와 금액을 확인해 주세요.");
        if (target == PaymentStatus.UNPAID && amount.signum() != 0) throw new IllegalArgumentException("미결제 금액은 0이어야 합니다.");
        if (target != PaymentStatus.UNPAID && (amount.signum() == 0 || date == null)) throw new IllegalArgumentException("결제 금액과 결제일은 필수입니다.");
        paymentStatus = target;
        paidAmount = amount;
        paidAt = target == PaymentStatus.UNPAID ? null : date;
        paymentEvidence = trimToNull(evidence);
    }

    public void updateProgress(BigDecimal shipped, BigDecimal received, BigDecimal ordered) {
        if (status == Status.CANCELLED || status == Status.DRAFT) return;
        if (received.compareTo(ordered) >= 0) status = Status.COMPLETED;
        else if (shipped.compareTo(ordered) >= 0) status = Status.SHIPPED;
        else if (shipped.signum() > 0) status = Status.PARTIALLY_SHIPPED;
        else status = Status.APPROVED;
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "은 필수입니다.");
        return value.trim();
    }
    private static String trimToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void touch() { updatedAt = LocalDateTime.now(); }
}

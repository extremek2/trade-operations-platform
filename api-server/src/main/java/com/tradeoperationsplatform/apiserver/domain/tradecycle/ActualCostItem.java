package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActualCostItem {
    public enum Type { PRODUCT, FREIGHT, DUTY, TAX, CLEARANCE, INSPECTION, WAREHOUSE, DOMESTIC_DELIVERY, OTHER }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id") private PurchaseOrder purchaseOrder;
    @Enumerated(EnumType.STRING) @Column(name = "cost_type", nullable = false) private Type costType;
    @Column(nullable = false) private String description;
    @Column(name = "amount_krw", nullable = false, precision = 19, scale = 2) private BigDecimal amountKrw;
    @Column(name = "incurred_at", nullable = false) private LocalDate incurredAt;
    @Column(name = "evidence_reference") private String evidenceReference;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public ActualCostItem(Organization organization, AppUser creator, PurchaseOrder order, Type type,
                          String description, BigDecimal amount, LocalDate incurredAt, String evidence) {
        this.publicId = UUID.randomUUID(); this.organization = organization; this.createdBy = creator;
        this.purchaseOrder = order; this.costType = type;
        if (description == null || description.isBlank()) throw new IllegalArgumentException("실제 비용 설명은 필수입니다.");
        this.description = description.trim(); this.amountKrw = amount;
        this.incurredAt = incurredAt == null ? LocalDate.now() : incurredAt;
        this.evidenceReference = evidence == null || evidence.isBlank() ? null : evidence.trim();
    }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

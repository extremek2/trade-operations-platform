package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reorder_decision")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReorderDecision {
    public enum Decision { REORDER, WATCH, STOP }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id") private PurchaseOrder purchaseOrder;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Decision decision;
    @Column(nullable = false) private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "decided_by") private AppUser decidedBy;
    @Column(name = "decided_at", nullable = false, updatable = false) private LocalDateTime decidedAt;
    public ReorderDecision(Organization organization, AppUser actor, PurchaseOrder order,
                           Product product, Decision decision, String reason) {
        this.publicId = UUID.randomUUID(); this.organization = organization; this.decidedBy = actor;
        this.purchaseOrder = order; this.product = product; this.decision = decision;
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("판단 근거는 필수입니다.");
        this.reason = reason.trim();
    }
    @PrePersist void create() { decidedAt = LocalDateTime.now(); }
}

package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "actual_cost_close")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActualCostClose {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_id", unique = true) private PurchaseOrder purchaseOrder;
    @Column(name = "expected_total_krw", nullable = false, precision = 19, scale = 2) private BigDecimal expectedTotalKrw;
    @Column(name = "actual_total_krw", nullable = false, precision = 19, scale = 2) private BigDecimal actualTotalKrw;
    @Column(name = "variance_krw", nullable = false, precision = 19, scale = 2) private BigDecimal varianceKrw;
    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal receivedQuantity;
    @Column(name = "actual_unit_cost_krw", nullable = false, precision = 19, scale = 2) private BigDecimal actualUnitCostKrw;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "closed_by") private AppUser closedBy;
    @Column(name = "closed_at", nullable = false, updatable = false) private LocalDateTime closedAt;
    @OneToMany(mappedBy = "costClose", cascade = CascadeType.ALL) private List<ActualCostAllocation> allocations = new ArrayList<>();

    public ActualCostClose(Organization organization, AppUser actor, PurchaseOrder order,
                           BigDecimal actualTotal, BigDecimal receivedQuantity) {
        this.publicId = UUID.randomUUID(); this.organization = organization; this.closedBy = actor;
        this.purchaseOrder = order; this.expectedTotalKrw = order.getExpectedTotalCostKrw();
        this.actualTotalKrw = actualTotal.setScale(2, RoundingMode.HALF_UP);
        this.varianceKrw = this.actualTotalKrw.subtract(expectedTotalKrw).setScale(2, RoundingMode.HALF_UP);
        this.receivedQuantity = receivedQuantity;
        this.actualUnitCostKrw = actualTotal.divide(receivedQuantity, 2, RoundingMode.HALF_UP);
    }
    public void addAllocation(ActualCostAllocation allocation) { allocations.add(allocation); }
    @PrePersist void create() { closedAt = LocalDateTime.now(); }
}

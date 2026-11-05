package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "actual_cost_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActualCostAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cost_close_id") private ActualCostClose costClose;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_lot_id") private InventoryLot inventoryLot;
    @Column(name = "allocated_amount_krw", nullable = false, precision = 19, scale = 2) private BigDecimal allocatedAmountKrw;
    public ActualCostAllocation(ActualCostClose close, InventoryLot lot, BigDecimal amount) {
        this.costClose = close; this.inventoryLot = lot; this.allocatedAmountKrw = amount;
    }
}

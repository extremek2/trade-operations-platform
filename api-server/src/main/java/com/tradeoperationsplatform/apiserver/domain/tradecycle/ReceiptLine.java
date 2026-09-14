package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "receipt_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private Receipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_allocation_id") private ShipmentAllocation allocation;
    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.ALL) @JoinColumn(name = "inventory_lot_id", unique = true) private InventoryLot inventoryLot;
    @Column(name = "received_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal receivedQuantity;

    public ReceiptLine(Receipt receipt, ShipmentAllocation allocation, InventoryLot lot, BigDecimal quantity) {
        this.receipt = receipt;
        this.allocation = allocation;
        this.inventoryLot = lot;
        this.receivedQuantity = quantity;
    }
}

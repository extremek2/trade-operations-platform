package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentAllocation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_case_id") private ShipmentCase shipment;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_order_line_id") private PurchaseOrderLine purchaseOrderLine;
    @Column(name = "allocated_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal allocatedQuantity;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public ShipmentAllocation(Organization organization, AppUser creator, ShipmentCase shipment,
                              PurchaseOrderLine line, BigDecimal quantity) {
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.createdBy = creator;
        this.shipment = shipment;
        this.purchaseOrderLine = line;
        this.allocatedQuantity = quantity;
    }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

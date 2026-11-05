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
@Table(name = "sales_observation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SalesObservation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_lot_id") private InventoryLot inventoryLot;
    @Column(nullable = false) private String channel;
    @Column(name = "observed_at", nullable = false) private LocalDate observedAt;
    @Column(name = "sold_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal soldQuantity;
    @Column(name = "returned_quantity", nullable = false, precision = 19, scale = 6) private BigDecimal returnedQuantity;
    @Column(name = "gross_revenue_krw", nullable = false, precision = 19, scale = 2) private BigDecimal grossRevenueKrw;
    @Column(name = "channel_cost_krw", nullable = false, precision = 19, scale = 2) private BigDecimal channelCostKrw;
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    public SalesObservation(Organization organization, AppUser creator, InventoryLot lot, String channel,
                            LocalDate observedAt, BigDecimal sold, BigDecimal returned,
                            BigDecimal revenue, BigDecimal channelCost, String notes) {
        this.publicId = UUID.randomUUID(); this.organization = organization; this.createdBy = creator;
        this.inventoryLot = lot;
        if (channel == null || channel.isBlank()) throw new IllegalArgumentException("판매 채널은 필수입니다.");
        this.channel = channel.trim(); this.observedAt = observedAt == null ? LocalDate.now() : observedAt;
        this.soldQuantity = sold; this.returnedQuantity = returned; this.grossRevenueKrw = revenue;
        this.channelCostKrw = channelCost; this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

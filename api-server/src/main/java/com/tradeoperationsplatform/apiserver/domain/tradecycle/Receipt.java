package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.ShipmentCase;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Receipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_case_id") private ShipmentCase shipment;
    @Column(name = "receipt_number", nullable = false) private String receiptNumber;
    @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt;
    private String notes;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL) private List<ReceiptLine> lines = new ArrayList<>();

    public Receipt(Organization organization, AppUser creator, ShipmentCase shipment, String receiptNumber,
                   LocalDateTime receivedAt, String notes) {
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.createdBy = creator;
        this.shipment = shipment;
        if (receiptNumber == null || receiptNumber.isBlank()) throw new IllegalArgumentException("입고번호는 필수입니다.");
        this.receiptNumber = receiptNumber.trim();
        this.receivedAt = receivedAt == null ? LocalDateTime.now() : receivedAt;
        this.notes = notes == null || notes.isBlank() ? null : notes.trim();
    }
    public void addLine(ReceiptLine line) { lines.add(line); }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

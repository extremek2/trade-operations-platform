package com.tradeoperationsplatform.apiserver.domain.shipment.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transport_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportDocument {
    public enum Type { MBL, HBL, MAWB, HAWB, BOOKING, OTHER }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "shipment_case_id") private ShipmentCase shipmentCase;
    @Enumerated(EnumType.STRING) @Column(name = "document_type", nullable = false) private Type documentType;
    @Column(name = "document_number", nullable = false) private String documentNumber;
    @Column(name = "issuer_name") private String issuerName;
    @Column(name = "issued_at") private LocalDateTime issuedAt;
    @Column(name = "is_primary", nullable = false) private boolean primary;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public TransportDocument(ShipmentCase shipmentCase, Type type, String number, String issuerName, LocalDateTime issuedAt, boolean primary) {
        this.publicId = UUID.randomUUID(); this.shipmentCase = shipmentCase; this.documentType = type;
        this.documentNumber = number; this.issuerName = issuerName; this.issuedAt = issuedAt; this.primary = primary;
    }
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

package com.tradeoperationsplatform.apiserver.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {
    public enum Type { SHIPPER, FORWARDER, CUSTOMS_BROKER, CARRIER, TRANSPORTER, WAREHOUSE, OTHER }
    public enum Status { ACTIVE, INACTIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;
    @Column(nullable = false)
    private String name;
    @Enumerated(EnumType.STRING) @Column(name = "organization_type", nullable = false)
    private Type organizationType;
    @Column(name = "business_number")
    private String businessNumber;
    private String email;
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Organization(String name, Type type, String businessNumber, String email, String phone) {
        this.publicId = UUID.randomUUID();
        this.name = name;
        this.organizationType = type;
        this.businessNumber = businessNumber;
        this.email = email;
        this.phone = phone;
        this.status = Status.ACTIVE;
    }

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

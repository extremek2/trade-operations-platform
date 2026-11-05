package com.tradeoperationsplatform.apiserver.domain.product.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {
    public enum Status { DISCOVERED, SOURCING, REVIEWING, APPROVED, ACTIVE, REJECTED, DISCONTINUED }

    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.DISCOVERED, EnumSet.of(Status.SOURCING, Status.REJECTED),
            Status.SOURCING, EnumSet.of(Status.REVIEWING, Status.REJECTED),
            Status.REVIEWING, EnumSet.of(Status.SOURCING, Status.APPROVED, Status.REJECTED),
            Status.APPROVED, EnumSet.of(Status.REVIEWING, Status.ACTIVE, Status.REJECTED),
            Status.ACTIVE, EnumSet.of(Status.DISCONTINUED),
            Status.REJECTED, EnumSet.of(Status.SOURCING),
            Status.DISCONTINUED, EnumSet.of(Status.ACTIVE)
    );

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @Column(nullable = false) private String name;
    private String hypothesis;
    @Column(name = "internal_sku") private String internalSku;
    private String brand;
    private String category;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public Product(Organization organization, AppUser createdBy, String name, String hypothesis,
                   String internalSku, String brand, String category) {
        this.organization = organization;
        this.createdBy = createdBy;
        this.publicId = UUID.randomUUID();
        this.name = required(name, "상품명");
        this.hypothesis = trimToNull(hypothesis);
        this.internalSku = trimToNull(internalSku);
        this.brand = trimToNull(brand);
        this.category = trimToNull(category);
        this.status = Status.DISCOVERED;
    }

    public Status changeStatus(Status target) {
        if (target == null || !TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStateException("허용되지 않은 상품 상태 전이입니다: " + status + " -> " + target);
        }
        Status previous = status;
        status = target;
        return previous;
    }

    public void revise(String name, String hypothesis, String internalSku, String brand, String category) {
        this.name = required(name, "상품명");
        this.hypothesis = trimToNull(hypothesis);
        this.internalSku = trimToNull(internalSku);
        this.brand = trimToNull(brand);
        this.category = trimToNull(category);
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "은 필수입니다.");
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

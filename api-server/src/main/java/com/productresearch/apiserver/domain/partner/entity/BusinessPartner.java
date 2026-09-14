package com.productresearch.apiserver.domain.partner.entity;

import com.productresearch.apiserver.domain.identity.entity.Organization;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "business_partner")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BusinessPartner {
    public enum Role { SUPPLIER, FORWARDER, CUSTOMS_BROKER, CARRIER, TRANSPORTER, WAREHOUSE, OTHER }
    public enum Status { ACTIVE, INACTIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "owner_organization_id") private Organization ownerOrganization;
    @Column(nullable = false) private String name;
    @Column(name = "country_code") private String countryCode;
    @Column(name = "business_number") private String businessNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "business_partner_role", joinColumns = @JoinColumn(name = "business_partner_id"))
    @Enumerated(EnumType.STRING) @Column(name = "partner_role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public BusinessPartner(Organization organization, String name, String countryCode, String businessNumber, Role role) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("거래처명은 필수입니다.");
        this.publicId = UUID.randomUUID();
        this.ownerOrganization = organization;
        this.name = name.trim();
        this.countryCode = normalizeCountry(countryCode);
        this.businessNumber = blankToNull(businessNumber);
        this.status = Status.ACTIVE;
        this.roles.add(Objects.requireNonNull(role));
    }

    public boolean hasRole(Role role) { return roles.contains(role); }
    public void addRole(Role role) { roles.add(Objects.requireNonNull(role)); }

    private static String normalizeCountry(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        if (normalized.length() != 2) throw new IllegalArgumentException("국가 코드는 ISO 2자리여야 합니다.");
        return normalized.toUpperCase(Locale.ROOT);
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

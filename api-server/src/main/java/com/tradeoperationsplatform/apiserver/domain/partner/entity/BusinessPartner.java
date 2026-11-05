package com.tradeoperationsplatform.apiserver.domain.partner.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
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
    @Column(name = "inquiry_email", length = 320) private String inquiryEmail;
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "business_partner_inquiry_cc", joinColumns = @JoinColumn(name = "business_partner_id"))
    @Column(name = "email", nullable = false, length = 320)
    private Set<String> inquiryCcEmails = new LinkedHashSet<>();
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Version @Column(nullable = false) private long version;
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

    public void setInquiryContacts(String email, Collection<String> ccEmails) {
        if (!hasRole(Role.SUPPLIER)) throw new IllegalArgumentException("상품 문의 주소는 공급처에만 등록할 수 있습니다.");
        String recipient = normalizeEmail(email);
        Collection<String> requestedCc = ccEmails == null ? List.of() : ccEmails;
        if (requestedCc.size() > 10) throw new IllegalArgumentException("참조 주소는 최대 10개까지 등록할 수 있습니다.");
        LinkedHashSet<String> normalizedCc = new LinkedHashSet<>();
        for (String cc : requestedCc) {
            String normalized = normalizeEmail(cc);
            if (normalized == null) throw new IllegalArgumentException("빈 참조 주소는 등록할 수 없습니다.");
            if (normalized.equals(recipient) || !normalizedCc.add(normalized))
                throw new IllegalArgumentException("받는 사람과 참조 주소는 중복할 수 없습니다.");
        }
        if (recipient == null && !normalizedCc.isEmpty())
            throw new IllegalArgumentException("참조 주소를 등록하려면 받는 사람 주소가 필요합니다.");
        this.inquiryEmail = recipient;
        this.inquiryCcEmails.clear();
        this.inquiryCcEmails.addAll(normalizedCc);
    }

    private static String normalizeEmail(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) return null;
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !normalized.matches("^[^\\s@,;<>]+@[^\\s@,;<>]+\\.[^\\s@,;<>]+$"))
            throw new IllegalArgumentException("올바른 이메일 주소를 입력해 주세요.");
        return normalized;
    }

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

package com.productresearch.apiserver.domain.sourcing.entity;

import com.productresearch.apiserver.domain.identity.entity.*;
import com.productresearch.apiserver.domain.partner.entity.BusinessPartner;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.*;
import java.util.*;

@Entity
@Table(name = "supplier_quote")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierQuote {
    public enum Status { DRAFT, RECEIVED, SELECTED, EXPIRED, REJECTED }
    private static final Map<Status, Set<Status>> TRANSITIONS = Map.of(
            Status.DRAFT, EnumSet.of(Status.RECEIVED, Status.REJECTED),
            Status.RECEIVED, EnumSet.of(Status.SELECTED, Status.EXPIRED, Status.REJECTED),
            Status.SELECTED, EnumSet.noneOf(Status.class),
            Status.EXPIRED, EnumSet.noneOf(Status.class),
            Status.REJECTED, EnumSet.noneOf(Status.class)
    );

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "business_partner_id") private BusinessPartner supplier;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "previous_quote_id") private SupplierQuote previousQuote;
    @Column(name = "revision_number", nullable = false) private int revisionNumber;
    @Column(name = "quote_number") private String quoteNumber;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "quoted_at") private LocalDate quotedAt;
    @Column(name = "valid_until") private LocalDate validUntil;
    private String notes;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by") private AppUser createdBy;
    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNumber ASC") private List<SupplierQuoteLine> lines = new ArrayList<>();
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public SupplierQuote(Organization organization, AppUser createdBy, BusinessPartner supplier,
                         String quoteNumber, String currency, LocalDate quotedAt, LocalDate validUntil, String notes) {
        this(organization, createdBy, supplier, null, 1, quoteNumber, currency, quotedAt, validUntil, notes);
    }

    private SupplierQuote(Organization organization, AppUser createdBy, BusinessPartner supplier,
                          SupplierQuote previousQuote, int revisionNumber, String quoteNumber,
                          String currency, LocalDate quotedAt, LocalDate validUntil, String notes) {
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER)) throw new IllegalArgumentException("공급처 역할이 있는 거래처만 견적을 등록할 수 있습니다.");
        validateDates(quotedAt, validUntil);
        this.publicId = UUID.randomUUID();
        this.organization = organization;
        this.createdBy = createdBy;
        this.supplier = supplier;
        this.previousQuote = previousQuote;
        this.revisionNumber = revisionNumber;
        this.quoteNumber = blankToNull(quoteNumber);
        this.currency = normalizeCurrency(currency);
        this.status = Status.DRAFT;
        this.quotedAt = quotedAt;
        this.validUntil = validUntil;
        this.notes = blankToNull(notes);
    }

    public void addLine(SupplierQuoteLine line) { lines.add(Objects.requireNonNull(line)); }

    public void revise(String quoteNumber, String currency, LocalDate quotedAt, LocalDate validUntil, String notes) {
        if (status != Status.DRAFT) throw new IllegalStateException("작성 중인 견적만 내용을 수정할 수 있습니다.");
        validateDates(quotedAt, validUntil);
        String normalizedQuoteNumber = blankToNull(quoteNumber);
        if (previousQuote != null && !Objects.equals(this.quoteNumber, normalizedQuoteNumber)) {
            throw new IllegalArgumentException("개정본의 견적번호는 이전 견적과 같아야 합니다.");
        }
        this.quoteNumber = normalizedQuoteNumber;
        this.currency = normalizeCurrency(currency);
        this.quotedAt = quotedAt;
        this.validUntil = validUntil;
        this.notes = blankToNull(notes);
        this.lines.clear();
        // Line changes belong to the quote aggregate and must advance its optimistic-lock version.
        this.updatedAt = LocalDateTime.now();
    }

    public SupplierQuote createRevision(AppUser creator, String currency, LocalDate quotedAt,
                                        LocalDate validUntil, String notes) {
        if (status == Status.DRAFT) throw new IllegalStateException("작성 중 견적은 새 개정본 대신 내용을 수정해야 합니다.");
        return new SupplierQuote(organization, creator, supplier, this, revisionNumber + 1,
                quoteNumber, currency, quotedAt, validUntil, notes);
    }

    public void changeStatus(Status target) {
        if (target == null || !TRANSITIONS.get(status).contains(target)) {
            throw new IllegalStateException("허용되지 않은 견적 상태 전이입니다: " + status + " -> " + target);
        }
        status = target;
    }

    private static String normalizeCurrency(String value) {
        if (value == null || !value.trim().matches("[A-Za-z]{3}")) throw new IllegalArgumentException("통화는 ISO 3자리 코드여야 합니다.");
        return value.trim().toUpperCase(Locale.ROOT);
    }
    private static void validateDates(LocalDate quotedAt, LocalDate validUntil) {
        if (validUntil != null && quotedAt != null && validUntil.isBefore(quotedAt)) {
            throw new IllegalArgumentException("견적 유효일은 견적일보다 빠를 수 없습니다.");
        }
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "purchase_selection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PurchaseSelection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id") private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_offer_draft_id") private SupplierOfferDraft draft;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_quote_id") private SupplierQuote quote;
    @Version private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "selected_by") private AppUser selectedBy;
    @Column(name = "selected_at", nullable = false, updatable = false) private LocalDateTime selectedAt;
    @OneToMany(mappedBy = "selection", cascade = CascadeType.ALL)
    @OrderBy("lineNumber ASC") private List<PurchaseSelectionLine> lines = new ArrayList<>();

    public PurchaseSelection(Organization organization, SupplierOfferDraft draft, SupplierQuote quote, AppUser selectedBy) {
        this.publicId = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.draft = Objects.requireNonNull(draft);
        this.quote = Objects.requireNonNull(quote);
        this.selectedBy = Objects.requireNonNull(selectedBy);
    }

    public void addLine(PurchaseSelectionLine line) { lines.add(Objects.requireNonNull(line)); }
    @PrePersist void create() { selectedAt = LocalDateTime.now(); }
}

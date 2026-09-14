package com.tradeoperationsplatform.apiserver.domain.product.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "product_id") private Product product;
    @Enumerated(EnumType.STRING) @Column(name = "from_status") private Product.Status fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false) private Product.Status toStatus;
    @Column(nullable = false) private String reason;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "changed_by") private AppUser changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false) private LocalDateTime changedAt;

    public ProductStatusHistory(Product product, Product.Status fromStatus, Product.Status toStatus,
                                String reason, AppUser changedBy) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("상태 변경 이유는 필수입니다.");
        this.product = product;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason.trim();
        this.changedBy = changedBy;
        this.changedAt = LocalDateTime.now();
    }
}

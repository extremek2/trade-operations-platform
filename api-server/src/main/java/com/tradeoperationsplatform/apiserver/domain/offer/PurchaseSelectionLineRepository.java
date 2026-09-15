package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PurchaseSelectionLineRepository extends JpaRepository<PurchaseSelectionLine, Long> {
    boolean existsByOfferLineId(Long offerLineId);
}

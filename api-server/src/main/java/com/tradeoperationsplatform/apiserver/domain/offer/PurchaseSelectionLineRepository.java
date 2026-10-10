package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseSelectionLineRepository extends JpaRepository<PurchaseSelectionLine, Long> {
    boolean existsByOfferLineId(Long offerLineId);
    Optional<PurchaseSelectionLine> findByQuoteLineId(Long quoteLineId);
}

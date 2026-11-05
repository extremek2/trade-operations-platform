package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesObservationRepository extends JpaRepository<SalesObservation, Long> {
    List<SalesObservation> findAllByInventoryLotPurchaseOrderLinePurchaseOrderIdOrderByObservedAtAsc(Long orderId);
}

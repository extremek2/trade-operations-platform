package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActualCostItemRepository extends JpaRepository<ActualCostItem, Long> {
    List<ActualCostItem> findAllByPurchaseOrderIdOrderByCreatedAtAsc(Long orderId);
}

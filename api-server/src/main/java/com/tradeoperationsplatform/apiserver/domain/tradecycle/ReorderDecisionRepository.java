package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReorderDecisionRepository extends JpaRepository<ReorderDecision, Long> {
    List<ReorderDecision> findAllByPurchaseOrderIdOrderByDecidedAtDesc(Long orderId);
}

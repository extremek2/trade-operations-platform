package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActualCostCloseRepository extends JpaRepository<ActualCostClose, Long> {
    boolean existsByPurchaseOrderId(Long orderId);
    @EntityGraph(attributePaths = {"allocations", "allocations.inventoryLot"})
    Optional<ActualCostClose> findByPurchaseOrderId(Long orderId);
}

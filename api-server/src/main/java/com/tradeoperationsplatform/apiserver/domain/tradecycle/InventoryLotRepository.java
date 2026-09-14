package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {
    @EntityGraph(attributePaths = {"product", "purchaseOrderLine", "receipt"})
    Optional<InventoryLot> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    @EntityGraph(attributePaths = {"product", "purchaseOrderLine", "receipt"})
    List<InventoryLot> findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(Long orderId);
    boolean existsByOrganizationIdAndLotNumber(Long organizationId, String lotNumber);
}

package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentAllocationRepository extends JpaRepository<ShipmentAllocation, Long> {
    Optional<ShipmentAllocation> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    List<ShipmentAllocation> findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(Long purchaseOrderId);
    List<ShipmentAllocation> findAllByShipmentIdOrderByCreatedAtAsc(Long shipmentId);
    boolean existsByShipmentIdAndPurchaseOrderLineId(Long shipmentId, Long lineId);
    @Query("select coalesce(sum(a.allocatedQuantity), 0) from ShipmentAllocation a where a.purchaseOrderLine.id=:lineId")
    BigDecimal sumAllocated(@Param("lineId") Long lineId);
}

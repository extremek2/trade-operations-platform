package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentAllocationRepository extends JpaRepository<ShipmentAllocation, Long> {
    Optional<ShipmentAllocation> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ShipmentAllocation a where a.publicId = :publicId and a.organization.id = :organizationId")
    Optional<ShipmentAllocation> findForUpdate(@Param("publicId") UUID publicId,
                                                @Param("organizationId") Long organizationId);

    List<ShipmentAllocation> findAllByPurchaseOrderLinePurchaseOrderIdOrderByCreatedAtAsc(Long purchaseOrderId);
    List<ShipmentAllocation> findAllByShipmentIdOrderByCreatedAtAsc(Long shipmentId);
    boolean existsByShipmentIdAndPurchaseOrderLineId(Long shipmentId, Long lineId);
    @Query("select coalesce(sum(a.allocatedQuantity), 0) from ShipmentAllocation a where a.purchaseOrderLine.id=:lineId")
    BigDecimal sumAllocated(@Param("lineId") Long lineId);
}

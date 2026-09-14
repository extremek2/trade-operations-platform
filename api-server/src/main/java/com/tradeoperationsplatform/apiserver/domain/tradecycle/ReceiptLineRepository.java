package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ReceiptLineRepository extends JpaRepository<ReceiptLine, Long> {
    @Query("select coalesce(sum(r.receivedQuantity), 0) from ReceiptLine r where r.allocation.id=:allocationId")
    BigDecimal sumReceivedByAllocation(@Param("allocationId") Long allocationId);
    @Query("select coalesce(sum(r.receivedQuantity), 0) from ReceiptLine r where r.allocation.purchaseOrderLine.purchaseOrder.id=:orderId")
    BigDecimal sumReceivedByOrder(@Param("orderId") Long orderId);
}

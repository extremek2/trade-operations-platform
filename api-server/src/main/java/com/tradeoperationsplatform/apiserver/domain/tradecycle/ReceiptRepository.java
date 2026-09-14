package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {
    boolean existsByOrganizationIdAndReceiptNumber(Long organizationId, String receiptNumber);
    List<Receipt> findAllByShipmentIdOrderByReceivedAtAsc(Long shipmentId);
}

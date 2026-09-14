package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {
    @EntityGraph(attributePaths = {"purchaseOrder", "product"})
    Optional<PurchaseOrderLine> findByPublicIdAndPurchaseOrderOrganizationId(UUID publicId, Long organizationId);
}

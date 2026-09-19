package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product", "lines.costScenario"})
    List<PurchaseOrder> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product", "lines.costScenario"})
    Optional<PurchaseOrder> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    boolean existsByOrganizationIdAndOrderNumber(Long organizationId, String orderNumber);
}

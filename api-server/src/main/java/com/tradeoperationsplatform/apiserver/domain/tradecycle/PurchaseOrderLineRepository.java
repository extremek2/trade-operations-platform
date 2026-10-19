package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, Long> {
    @EntityGraph(attributePaths = {"purchaseOrder", "product"})
    Optional<PurchaseOrderLine> findByPublicIdAndPurchaseOrderOrganizationId(UUID publicId, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from PurchaseOrderLine l where l.publicId = :publicId and l.purchaseOrder.organization.id = :organizationId")
    Optional<PurchaseOrderLine> findForUpdate(@Param("publicId") UUID publicId,
                                              @Param("organizationId") Long organizationId);

    boolean existsByCostScenarioId(Long costScenarioId);
}

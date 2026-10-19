package com.tradeoperationsplatform.apiserver.domain.tradecycle;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product", "lines.costScenario"})
    List<PurchaseOrder> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product", "lines.costScenario"})
    Optional<PurchaseOrder> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.publicId = :publicId and o.organization.id = :organizationId")
    Optional<PurchaseOrder> findForUpdate(@Param("publicId") UUID publicId,
                                          @Param("organizationId") Long organizationId);

    boolean existsByOrganizationIdAndOrderNumber(Long organizationId, String orderNumber);
}

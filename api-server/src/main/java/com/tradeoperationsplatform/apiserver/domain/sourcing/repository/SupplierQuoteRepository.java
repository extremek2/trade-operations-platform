package com.tradeoperationsplatform.apiserver.domain.sourcing.repository;

import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface SupplierQuoteRepository extends JpaRepository<SupplierQuote, Long> {
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product"})
    List<SupplierQuote> findAllByOrganizationIdOrderByUpdatedAtDesc(Long organizationId);
    @EntityGraph(attributePaths = {"supplier", "lines", "lines.product"})
    Optional<SupplierQuote> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    @Query("select (count(q)>0) from SupplierQuote q where q.organization.id=:organizationId and q.supplier.id=:supplierId and q.quoteNumber=:quoteNumber and (:excludedId is null or q.id<>:excludedId)")
    boolean existsQuoteNumber(@Param("organizationId") Long organizationId,
                              @Param("supplierId") Long supplierId,
                              @Param("quoteNumber") String quoteNumber,
                              @Param("excludedId") Long excludedId);
    boolean existsByPreviousQuoteId(Long previousQuoteId);
}

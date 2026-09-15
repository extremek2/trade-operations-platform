package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    boolean existsByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
}

package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    boolean existsByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
    Optional<SourceArtifact> findByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
}

package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    boolean existsByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
    Optional<SourceArtifact> findByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
    Optional<SourceArtifact> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    List<SourceArtifact> findAllByOrganizationIdAndSourceTypeInOrderByReceivedAtDesc(
            Long organizationId, Set<SourceArtifact.SourceType> sourceTypes);
}

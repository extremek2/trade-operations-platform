package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SourceArtifactRepository extends JpaRepository<SourceArtifact, Long> {
    boolean existsByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
    Optional<SourceArtifact> findByOrganizationIdAndSupplierIdAndContentHash(Long organizationId, Long supplierId, String contentHash);
    Optional<SourceArtifact> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select artifact from SourceArtifact artifact where artifact.publicId = :publicId and artifact.organization.id = :organizationId")
    Optional<SourceArtifact> findDocumentForUpdate(@Param("publicId") UUID publicId,
                                                   @Param("organizationId") Long organizationId);
    List<SourceArtifact> findAllByOrganizationIdAndSourceTypeInOrderByReceivedAtDesc(
            Long organizationId, Set<SourceArtifact.SourceType> sourceTypes);
}

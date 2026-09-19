package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface SupplierOfferDraftRepository extends JpaRepository<SupplierOfferDraft, Long> {
    Optional<SupplierOfferDraft> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
    Optional<SupplierOfferDraft> findByExtractionRunSourceArtifactId(Long sourceArtifactId);
    List<SupplierOfferDraft> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
}

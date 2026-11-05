package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface PurchaseSelectionRepository extends JpaRepository<PurchaseSelection, Long> {
    List<PurchaseSelection> findAllByOrganizationIdOrderBySelectedAtDesc(Long organizationId);
    Optional<PurchaseSelection> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);
}

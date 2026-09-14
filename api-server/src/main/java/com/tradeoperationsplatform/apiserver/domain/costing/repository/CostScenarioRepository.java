package com.tradeoperationsplatform.apiserver.domain.costing.repository;

import com.tradeoperationsplatform.apiserver.domain.costing.entity.CostScenario;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CostScenarioRepository extends JpaRepository<CostScenario, Long> {
    @EntityGraph(attributePaths = {"quote", "quote.supplier", "product", "previousScenario"})
    List<CostScenario> findAllByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    @EntityGraph(attributePaths = {"quote", "quote.supplier", "product", "previousScenario"})
    Optional<CostScenario> findByPublicIdAndOrganizationId(UUID publicId, Long organizationId);

    boolean existsByPreviousScenarioId(Long previousScenarioId);
}

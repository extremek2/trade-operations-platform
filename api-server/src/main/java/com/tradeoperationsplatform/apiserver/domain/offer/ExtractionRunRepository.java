package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, Long> {
    Optional<ExtractionRun> findTopBySourceArtifactIdOrderByCreatedAtDesc(Long sourceArtifactId);
}

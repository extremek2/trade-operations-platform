package com.tradeoperationsplatform.apiserver.domain.offer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface ExtractionRunRepository extends JpaRepository<ExtractionRun, Long> {
    Optional<ExtractionRun> findTopBySourceArtifactIdOrderByIdDesc(Long sourceArtifactId);
    List<ExtractionRun> findAllBySourceArtifactIdOrderByIdDesc(Long sourceArtifactId);
}

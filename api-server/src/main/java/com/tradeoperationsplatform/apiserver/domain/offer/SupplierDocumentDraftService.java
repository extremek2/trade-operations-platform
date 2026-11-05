package com.tradeoperationsplatform.apiserver.domain.offer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class SupplierDocumentDraftService {
    private final SourceArtifactRepository artifacts;
    private final ExtractionRunRepository runs;
    private final SupplierOfferDraftRepository drafts;
    private final DocumentOfferCandidateParser candidateParser;
    private final ObjectMapper objectMapper;
    private final CurrentActor currentActor;

    @Transactional
    public SupplierOfferResponse create(UUID documentId, String currency) {
        var actor = currentActor.require();
        SourceArtifact artifact = artifacts.findByPublicIdAndOrganizationId(documentId, actor.organization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("공급 문서를 찾을 수 없습니다."));
        if (artifact.getSourceType() != SourceArtifact.SourceType.PDF
                && artifact.getSourceType() != SourceArtifact.SourceType.IMAGE)
            throw new ResourceNotFoundException("공급 문서를 찾을 수 없습니다.");

        var existing = drafts.findByExtractionRunSourceArtifactId(artifact.getId());
        if (existing.isPresent()) return SupplierOfferResponse.from(existing.get());

        ExtractionRun run = runs.findTopBySourceArtifactIdOrderByIdDesc(artifact.getId())
                .orElseThrow(() -> new BusinessException("문서 추출 이력이 없습니다."));
        if (run.getStatus() != ExtractionRun.Status.SUCCEEDED)
            throw new BusinessException("문서 추출이 완료된 뒤 검토 초안을 만들 수 있습니다.");

        List<DocumentOfferCandidateParser.Candidate> candidates = candidateParser.parse(run.getExtractedText());
        List<BigDecimal> lineConfidences = lineConfidences(run);
        SupplierOfferDraft draft = new SupplierOfferDraft(actor.organization(), artifact.getSupplier(), run,
                actor.user(), currency);
        for (DocumentOfferCandidateParser.Candidate candidate : candidates) {
            SupplierOfferDraftLine line = new SupplierOfferDraftLine(draft, draft.getLines().size() + 1,
                    candidate.originalText(), "텍스트 " + candidate.sourceLine() + "행");
            line.applyExtraction(candidate.reviewedName(), candidate.supplierSku(), candidate.quantityUnit(),
                    candidate.minimumQuantity(), candidate.unitPrice(), candidate.countryOfOrigin(),
                    null, candidate.warnings());
            if (candidate.sourceLine() <= lineConfidences.size())
                line.extractionConfidence(lineConfidences.get(candidate.sourceLine() - 1));
            draft.addLine(line);
        }
        return SupplierOfferResponse.from(drafts.saveAndFlush(draft));
    }

    private List<BigDecimal> lineConfidences(ExtractionRun run) {
        String text = run.getExtractedText().replace('\f', '\n');
        String[] sourceLines = text.split("\\R", -1);
        List<BigDecimal> result = new ArrayList<>();
        if (run.getMethod() == ExtractionRun.Method.PDF_TEXT) {
            for (String ignored : sourceLines) result.add(new BigDecimal("100.00"));
            return result;
        }
        try {
            JsonNode pages = objectMapper.readTree(run.getResultPayload()).path("pages");
            List<JsonNode> ocrLines = new ArrayList<>();
            for (JsonNode page : pages) for (JsonNode line : page.path("lines")) ocrLines.add(line);
            int cursor = 0;
            for (String sourceLine : sourceLines) {
                if (sourceLine.isBlank()) { result.add(null); continue; }
                if (cursor >= ocrLines.size() || !sourceLine.trim().equals(ocrLines.get(cursor).path("text").asText()))
                    return List.of();
                result.add(ocrLines.get(cursor++).path("confidence").decimalValue());
            }
            return cursor == ocrLines.size() ? result : List.of();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}

package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplierOfferService {
    private final SourceArtifactRepository artifacts;
    private final ExtractionRunRepository runs;
    private final SupplierOfferDraftRepository drafts;
    private final BusinessPartnerRepository partners;
    private final SupplierOfferFileParser fileParser;
    private final CurrentActor currentActor;

    @Transactional
    public SupplierOfferResponse create(SupplierOfferRequests.Create request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        BusinessPartner supplier = requireSupplier(request.supplierId(), organizationId);
        String hash = sha256(request.originalText());
        if (artifacts.existsByOrganizationIdAndSupplierIdAndContentHash(organizationId, supplier.getId(), hash))
            throw new ConflictException("같은 공급처의 동일한 원본이 이미 등록되었습니다.");
        try {
            SourceArtifact artifact = artifacts.saveAndFlush(new SourceArtifact(actor.organization(), supplier,
                    actor.user(), request.sourceReference(), request.originalText(), hash));
            ExtractionRun run = runs.saveAndFlush(new ExtractionRun(artifact));
            SupplierOfferDraft draft = new SupplierOfferDraft(actor.organization(), supplier, run, actor.user(),
                    request.currency());
            int number = 1;
            for (SupplierOfferRequests.OriginalLine line : request.lines())
                draft.addLine(new SupplierOfferDraftLine(draft, number++, line.originalName(), line.sourceLocation()));
            return SupplierOfferResponse.from(drafts.saveAndFlush(draft));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이 공급처 원본이 이미 등록되었습니다.");
        }
    }

    @Transactional
    public SupplierOfferResponse importFile(UUID supplierId, String currency, String sourceReference, MultipartFile file) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        BusinessPartner supplier = requireSupplier(supplierId, organizationId);
        if (currency == null || !currency.trim().matches("[A-Za-z]{3}"))
            throw new BusinessException("통화는 ISO 3자리 코드여야 합니다.");
        if (sourceReference != null && sourceReference.length() > 2000)
            throw new BusinessException("원본 위치는 2000자를 초과할 수 없습니다.");
        String fileName = safeFileName(file == null ? null : file.getOriginalFilename());
        byte[] content;
        try { content = file == null ? null : file.getBytes(); }
        catch (IOException e) { throw new BusinessException("원본 파일을 읽을 수 없습니다."); }
        String hash = sha256(content);
        SupplierOfferFileParser.ParsedOffer parsed;
        try { parsed = fileParser.parse(fileName, content); }
        catch (IllegalArgumentException e) { throw new BusinessException(e.getMessage()); }
        SourceArtifact.SourceType sourceType = SourceArtifact.SourceType.valueOf(parsed.format().name());
        Optional<SourceArtifact> existing = artifacts.findByOrganizationIdAndSupplierIdAndContentHash(
                organizationId, supplier.getId(), hash);
        if (existing.isPresent()) {
            if (existing.get().getSourceType() != sourceType)
                throw new ConflictException("같은 내용이 다른 원본 형식으로 이미 등록되었습니다.");
            return drafts.findByExtractionRunSourceArtifactId(existing.get().getId())
                    .map(SupplierOfferResponse::from)
                    .orElseThrow(() -> new ConflictException("동일 원본은 있지만 연결된 검토 초안을 찾을 수 없습니다."));
        }
        ExtractionRun.Method method = ExtractionRun.Method.valueOf(parsed.format().name());
        String contentType = parsed.format() == SupplierOfferFileParser.Format.CSV
                ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        try {
            SourceArtifact artifact = artifacts.saveAndFlush(new SourceArtifact(actor.organization(), supplier,
                    actor.user(), sourceType, sourceReference, fileName, contentType, content, hash));
            ExtractionRun run = runs.saveAndFlush(new ExtractionRun(artifact, method,
                    SupplierOfferFileParser.EXTRACTOR_VERSION));
            SupplierOfferDraft draft = new SupplierOfferDraft(actor.organization(), supplier, run, actor.user(), currency);
            int lineNumber = 1;
            for (SupplierOfferFileParser.ParsedLine parsedLine : parsed.lines()) {
                SupplierOfferDraftLine line = new SupplierOfferDraftLine(draft, lineNumber++,
                        parsedLine.originalName(), parsedLine.sourceLocation());
                line.applyExtraction(parsedLine.originalName(), parsedLine.supplierSku(), parsedLine.quantityUnit(),
                        parsedLine.minimumQuantity(), parsedLine.unitPrice(), parsedLine.countryOfOrigin(),
                        parsedLine.notes(), parsedLine.errors());
                draft.addLine(line);
            }
            return SupplierOfferResponse.from(drafts.saveAndFlush(draft));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("이 공급처 원본이 이미 등록되었습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<SupplierOfferResponse> findAll() {
        Long organizationId = currentActor.require().organization().getId();
        return drafts.findAllByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(SupplierOfferResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SupplierOfferResponse findOne(UUID draftId) { return SupplierOfferResponse.from(require(draftId)); }

    @Transactional
    public SupplierOfferResponse reviewLine(UUID draftId, int lineNumber, SupplierOfferRequests.ReviewLine request) {
        SupplierOfferDraft draft = require(draftId);
        checkVersion(draft, request.version());
        SupplierOfferDraftLine line = requireLine(draft, lineNumber);
        try {
            line.review(request.reviewedName(), request.supplierSku(), request.quantityUnit(),
                    request.minimumQuantity(), request.unitPrice(), request.countryOfOrigin(), request.notes());
            line.reviewedBy(currentActor.require().user());
            draft.touchReview();
            drafts.flush();
            return SupplierOfferResponse.from(draft);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    @Transactional
    public SupplierOfferResponse excludeLine(UUID draftId, int lineNumber, SupplierOfferRequests.Action request) {
        SupplierOfferDraft draft = require(draftId);
        checkVersion(draft, request.version());
        SupplierOfferDraftLine line = requireLine(draft, lineNumber);
        try {
            line.exclude();
            line.reviewedBy(currentActor.require().user());
            draft.touchReview();
            drafts.flush();
            return SupplierOfferResponse.from(draft);
        } catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
    }

    @Transactional
    public SupplierOfferResponse confirm(UUID draftId, SupplierOfferRequests.Action request) {
        SupplierOfferDraft draft = require(draftId);
        checkVersion(draft, request.version());
        try {
            draft.confirm();
            drafts.flush();
            return SupplierOfferResponse.from(draft);
        } catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
    }

    private SupplierOfferDraft require(UUID draftId) {
        Long organizationId = currentActor.require().organization().getId();
        return drafts.findByPublicIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급 제안 초안을 찾을 수 없습니다."));
    }
    private BusinessPartner requireSupplier(UUID supplierId, Long organizationId) {
        BusinessPartner supplier = partners.findOwnedWithRoles(supplierId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급처를 찾을 수 없습니다."));
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER) || supplier.getStatus() != BusinessPartner.Status.ACTIVE)
            throw new BusinessException("활성 공급처만 제안을 등록할 수 있습니다.");
        return supplier;
    }
    private String safeFileName(String original) {
        if (original == null || original.isBlank()) throw new BusinessException("원본 파일명이 필요합니다.");
        String name = Path.of(original.replace('\\', '/')).getFileName().toString().trim();
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(character -> Character.isISOControl(character)))
            throw new BusinessException("원본 파일명이 올바르지 않습니다.");
        return name;
    }
    private SupplierOfferDraftLine requireLine(SupplierOfferDraft draft, int lineNumber) {
        return draft.getLines().stream().filter(line -> line.getLineNumber() == lineNumber).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("공급 제안 행을 찾을 수 없습니다."));
    }
    private void checkVersion(SupplierOfferDraft draft, long version) {
        if (draft.getVersion() != version) throw new ConflictException("공급 제안 초안이 변경되었습니다.");
    }
    private static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }
    private static String sha256(byte[] content) {
        if (content == null || content.length == 0) throw new BusinessException("비어 있는 파일은 가져올 수 없습니다.");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e); }
    }
}

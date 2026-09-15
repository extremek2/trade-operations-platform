package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
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
    private final CurrentActor currentActor;

    @Transactional
    public SupplierOfferResponse create(SupplierOfferRequests.Create request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        BusinessPartner supplier = partners.findOwnedWithRoles(request.supplierId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급처를 찾을 수 없습니다."));
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER) || supplier.getStatus() != BusinessPartner.Status.ACTIVE)
            throw new BusinessException("활성 공급처만 제안을 등록할 수 있습니다.");
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
    private SupplierOfferDraftLine requireLine(SupplierOfferDraft draft, int lineNumber) {
        return draft.getLines().stream().filter(line -> line.getLineNumber() == lineNumber).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("공급 제안 행을 찾을 수 없습니다."));
    }
    private void checkVersion(SupplierOfferDraft draft, long version) {
        if (draft.getVersion() != version) throw new ConflictException("공급 제안 초안이 변경되었습니다.");
    }
    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e); }
    }
}

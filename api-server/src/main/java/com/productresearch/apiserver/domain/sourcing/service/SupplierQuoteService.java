package com.productresearch.apiserver.domain.sourcing.service;

import com.productresearch.apiserver.domain.auth.service.CurrentActor;
import com.productresearch.apiserver.domain.partner.entity.BusinessPartner;
import com.productresearch.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.productresearch.apiserver.domain.product.entity.Product;
import com.productresearch.apiserver.domain.product.repository.ProductRepository;
import com.productresearch.apiserver.domain.sourcing.dto.*;
import com.productresearch.apiserver.domain.sourcing.entity.*;
import com.productresearch.apiserver.domain.sourcing.repository.SupplierQuoteRepository;
import com.productresearch.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SupplierQuoteService {
    private final SupplierQuoteRepository quotes;
    private final BusinessPartnerRepository partners;
    private final ProductRepository products;
    private final CurrentActor currentActor;

    @Transactional
    public QuoteResponse create(QuoteRequests.Create request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        String quoteNumber = blankToNull(request.quoteNumber());
        BusinessPartner supplier = partners.findOwnedWithRoles(request.supplierId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급처를 찾을 수 없습니다."));
        if (!supplier.hasRole(BusinessPartner.Role.SUPPLIER)) throw new BusinessException("공급처 역할이 없는 거래처입니다.");
        if (supplier.getStatus() != BusinessPartner.Status.ACTIVE) throw new BusinessException("비활성 거래처에는 견적을 등록할 수 없습니다.");
        ensureQuoteNumberAvailable(organizationId, supplier.getId(), quoteNumber, null);

        SupplierQuote quote;
        try {
            quote = new SupplierQuote(actor.organization(), actor.user(), supplier, quoteNumber, request.currency(),
                    request.quotedAt(), request.validUntil(), request.notes());
            addLines(quote, request.lines(), organizationId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        return QuoteResponse.from(quotes.save(quote));
    }

    @Transactional(readOnly = true)
    public List<QuoteResponse> findAll() {
        var actor = currentActor.require();
        return quotes.findAllByOrganizationIdOrderByUpdatedAtDesc(actor.organization().getId()).stream()
                .map(QuoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public QuoteResponse findOne(UUID quoteId) {
        var actor = currentActor.require();
        return QuoteResponse.from(require(quoteId, actor.organization().getId()));
    }

    @Transactional
    public QuoteResponse revise(UUID quoteId, QuoteRequests.Revise request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        SupplierQuote quote = require(quoteId, organizationId);
        if (quote.getVersion() != request.version()) throw new ConflictException("견적 정보가 변경되었습니다.");
        String quoteNumber = blankToNull(request.quoteNumber());
        if (!Objects.equals(quote.getQuoteNumber(), quoteNumber)) {
            ensureQuoteNumberAvailable(organizationId, quote.getSupplier().getId(), quoteNumber, quote.getId());
        }
        try {
            quote.revise(quoteNumber, request.currency(), request.quotedAt(), request.validUntil(), request.notes());
            // Delete orphaned lines before the replacement lines reuse their line numbers.
            quotes.flush();
            addLines(quote, request.lines(), organizationId);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }
        quotes.flush();
        return QuoteResponse.from(quote);
    }

    @Transactional
    public QuoteResponse createRevision(UUID quoteId, QuoteRequests.CreateRevision request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        SupplierQuote previous = require(quoteId, organizationId);
        if (previous.getVersion() != request.version()) throw new ConflictException("견적 정보가 변경되었습니다.");
        if (quotes.existsByPreviousQuoteId(previous.getId())) throw new ConflictException("이미 다음 개정본이 존재합니다.");
        try {
            SupplierQuote revision = previous.createRevision(actor.user(), request.currency(), request.quotedAt(),
                    request.validUntil(), request.notes());
            addLines(revision, request.lines(), organizationId);
            return QuoteResponse.from(quotes.save(revision));
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    @Transactional
    public QuoteResponse changeStatus(UUID quoteId, QuoteRequests.ChangeStatus request) {
        var actor = currentActor.require();
        SupplierQuote quote = require(quoteId, actor.organization().getId());
        if (quote.getVersion() != request.version()) throw new ConflictException("견적 상태가 변경되었습니다.");
        try { quote.changeStatus(request.status()); }
        catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
        quotes.flush();
        return QuoteResponse.from(quote);
    }

    private SupplierQuote require(UUID quoteId, Long organizationId) {
        return quotes.findByPublicIdAndOrganizationId(quoteId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("견적을 찾을 수 없습니다."));
    }
    private void addLines(SupplierQuote quote, List<QuoteRequests.Line> inputs, Long organizationId) {
        int lineNumber = 1;
        Set<UUID> uniqueProducts = new HashSet<>();
        for (QuoteRequests.Line input : inputs) {
            if (!uniqueProducts.add(input.productId())) throw new BusinessException("한 견적에 같은 상품을 중복 등록할 수 없습니다.");
            Product product = products.findByPublicIdAndOrganizationId(input.productId(), organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("견적 상품을 찾을 수 없습니다."));
            if (!EnumSet.of(Product.Status.SOURCING, Product.Status.REVIEWING).contains(product.getStatus())) {
                throw new BusinessException("소싱 또는 검토 중인 상품만 견적에 포함할 수 있습니다.");
            }
            quote.addLine(new SupplierQuoteLine(quote, product, lineNumber++, input.supplierSku(),
                    input.description(), input.minimumQuantity(), input.quantityUnit(), input.unitPrice(),
                    input.countryOfOrigin(), input.leadTimeDays(), input.notes()));
        }
    }
    private void ensureQuoteNumberAvailable(Long organizationId, Long supplierId, String quoteNumber, Long excludedId) {
        if (quoteNumber != null && quotes.existsQuoteNumber(organizationId, supplierId, quoteNumber, excludedId)) {
            throw new ConflictException("이 공급처에 이미 사용 중인 견적번호입니다.");
        }
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

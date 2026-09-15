package com.tradeoperationsplatform.apiserver.domain.offer;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import com.tradeoperationsplatform.apiserver.domain.product.entity.ProductStatusHistory;
import com.tradeoperationsplatform.apiserver.domain.product.repository.ProductRepository;
import com.tradeoperationsplatform.apiserver.domain.product.repository.ProductStatusHistoryRepository;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuoteLine;
import com.tradeoperationsplatform.apiserver.domain.sourcing.repository.SupplierQuoteRepository;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PurchaseSelectionService {
    private final SupplierOfferDraftRepository drafts;
    private final PurchaseSelectionRepository selections;
    private final PurchaseSelectionLineRepository selectionLines;
    private final ProductRepository products;
    private final ProductStatusHistoryRepository productHistory;
    private final SupplierQuoteRepository quotes;
    private final CurrentActor currentActor;

    @Transactional
    public PurchaseSelectionResponse create(UUID draftId, PurchaseSelectionRequests.Create request) {
        var actor = currentActor.require();
        Long organizationId = actor.organization().getId();
        SupplierOfferDraft draft = drafts.findByPublicIdAndOrganizationId(draftId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("공급 제안 초안을 찾을 수 없습니다."));
        if (draft.getVersion() != request.version()) throw new ConflictException("공급 제안이 변경되었습니다.");
        if (draft.getStatus() != SupplierOfferDraft.Status.CONFIRMED)
            throw new BusinessException("추출 확인 후에만 구매 품목을 선택할 수 있습니다.");
        Set<Integer> uniqueLines = new HashSet<>();
        Set<UUID> uniqueExistingProducts = new HashSet<>();
        List<ResolvedLine> resolved = new ArrayList<>();
        for (PurchaseSelectionRequests.Line input : request.lines()) {
            if (!uniqueLines.add(input.lineNumber())) throw new BusinessException("같은 제안 행을 중복 선택할 수 없습니다.");
            SupplierOfferDraftLine offerLine = draft.getLines().stream()
                    .filter(line -> line.getLineNumber() == input.lineNumber()).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("공급 제안 행을 찾을 수 없습니다."));
            if (offerLine.getStatus() != SupplierOfferDraftLine.Status.CONFIRMED)
                throw new BusinessException("확인된 행만 선택할 수 있습니다.");
            if (selectionLines.existsByOfferLineId(offerLine.getId()))
                throw new ConflictException("이미 구매 후보로 선택한 제안 행입니다.");
            if (offerLine.getQuantityUnit() == null)
                throw new BusinessException("희망 수량을 기록하려면 확인된 수량 단위가 필요합니다.");
            if (offerLine.getMinimumQuantity() != null && input.desiredQuantity().compareTo(offerLine.getMinimumQuantity()) < 0)
                throw new BusinessException("희망 수량이 공급처 MOQ보다 적습니다.");
            Product product = null;
            if (input.existingProductId() != null) {
                if (!uniqueExistingProducts.add(input.existingProductId()))
                    throw new BusinessException("한 선택 견적에 같은 상품을 중복 연결할 수 없습니다.");
                product = products.findByPublicIdAndOrganizationId(input.existingProductId(), organizationId)
                        .orElseThrow(() -> new ResourceNotFoundException("연결할 상품을 찾을 수 없습니다."));
                if (!EnumSet.of(Product.Status.SOURCING, Product.Status.REVIEWING).contains(product.getStatus()))
                    throw new BusinessException("소싱 또는 검토 중인 상품만 기존 원장과 연결할 수 있습니다.");
            }
            resolved.add(new ResolvedLine(offerLine, input.desiredQuantity(), product));
        }

        try {
            SupplierQuote quote = new SupplierQuote(actor.organization(), actor.user(), draft.getSupplier(),
                    "OFFER-" + UUID.randomUUID(), draft.getCurrency(), null, null,
                    "공급 제안 " + draft.getPublicId() + "에서 선택한 품목");
            int quoteLineNumber = 1;
            List<Product> resolvedProducts = new ArrayList<>();
            for (ResolvedLine item : resolved) {
                Product product = item.product();
                if (product == null) {
                    product = new Product(actor.organization(), actor.user(), item.offerLine().getReviewedName(),
                            "공급 제안 " + draft.getPublicId() + "에서 선택", null, null, null);
                    Product.Status from = product.changeStatus(Product.Status.SOURCING);
                    products.saveAndFlush(product);
                    productHistory.save(new ProductStatusHistory(product, from, Product.Status.SOURCING,
                            "공급 제안 행 " + item.offerLine().getLineNumber() + " 구매 후보 선택", actor.user()));
                }
                resolvedProducts.add(product);
                SupplierOfferDraftLine line = item.offerLine();
                quote.addLine(new SupplierQuoteLine(quote, product, quoteLineNumber++, line.getSupplierSku(),
                        line.getReviewedName(), line.getMinimumQuantity(), line.getQuantityUnit(),
                        line.getUnitPrice(), line.getCountryOfOrigin(), null, line.getNotes()));
            }
            quotes.saveAndFlush(quote);
            PurchaseSelection selection = new PurchaseSelection(actor.organization(), draft, quote, actor.user());
            for (int index = 0; index < resolved.size(); index++) {
                selection.addLine(new PurchaseSelectionLine(selection, resolved.get(index).offerLine(),
                        quote.getLines().get(index), resolvedProducts.get(index), index + 1,
                        resolved.get(index).desiredQuantity()));
            }
            selections.saveAndFlush(selection);
            draft.recordSelection();
            drafts.flush();
            return PurchaseSelectionResponse.from(selection);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BusinessException(e.getMessage());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("선택한 제안 행이나 견적이 이미 연결되었습니다.");
        }
    }

    @Transactional(readOnly = true)
    public List<PurchaseSelectionResponse> findAll() {
        Long organizationId = currentActor.require().organization().getId();
        return selections.findAllByOrganizationIdOrderBySelectedAtDesc(organizationId).stream()
                .map(PurchaseSelectionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseSelectionResponse findOne(UUID selectionId) {
        Long organizationId = currentActor.require().organization().getId();
        return PurchaseSelectionResponse.from(selections.findByPublicIdAndOrganizationId(selectionId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("구매 품목 선택을 찾을 수 없습니다.")));
    }

    private record ResolvedLine(SupplierOfferDraftLine offerLine, java.math.BigDecimal desiredQuantity, Product product) {}
}

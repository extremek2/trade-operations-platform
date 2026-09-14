package com.tradeoperationsplatform.apiserver.domain.product.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.product.dto.*;
import com.tradeoperationsplatform.apiserver.domain.product.entity.*;
import com.tradeoperationsplatform.apiserver.domain.product.repository.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository products;
    private final ProductStatusHistoryRepository history;
    private final CurrentActor currentActor;

    @Transactional
    public ProductResponse create(ProductRequests.Create request) {
        var actor = currentActor.require();
        String sku = normalize(request.internalSku());
        if (sku != null && products.existsByOrganizationIdAndInternalSku(actor.organization().getId(), sku)) {
            throw new ConflictException("이미 사용 중인 내부 SKU입니다.");
        }
        Product product = products.save(new Product(actor.organization(), actor.user(), request.name(),
                request.hypothesis(), sku, request.brand(), request.category()));
        history.save(new ProductStatusHistory(product, null, Product.Status.DISCOVERED, "상품 후보 등록", actor.user()));
        return ProductResponse.from(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> findAll() {
        var actor = currentActor.require();
        return products.findAllByOrganizationIdOrderByUpdatedAtDesc(actor.organization().getId()).stream()
                .map(ProductResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProductResponse findOne(UUID productId) {
        var actor = currentActor.require();
        return ProductResponse.from(require(productId, actor.organization().getId()));
    }

    @Transactional
    public ProductResponse revise(UUID productId, ProductRequests.Revise request) {
        var actor = currentActor.require();
        Product product = require(productId, actor.organization().getId());
        if (product.getVersion() != request.version()) throw new ConflictException("상품 정보가 변경되었습니다.");
        String sku = normalize(request.internalSku());
        if (!Objects.equals(product.getInternalSku(), sku) && sku != null
                && products.existsByOrganizationIdAndInternalSku(actor.organization().getId(), sku)) {
            throw new ConflictException("이미 사용 중인 내부 SKU입니다.");
        }
        product.revise(request.name(), request.hypothesis(), sku, request.brand(), request.category());
        products.flush();
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse changeStatus(UUID productId, ProductRequests.ChangeStatus request) {
        var actor = currentActor.require();
        Product product = require(productId, actor.organization().getId());
        if (product.getVersion() != request.version()) throw new ConflictException("상품 상태가 변경되었습니다.");
        Product.Status previous;
        try { previous = product.changeStatus(request.status()); }
        catch (IllegalStateException e) { throw new BusinessException(e.getMessage()); }
        history.save(new ProductStatusHistory(product, previous, request.status(), request.reason(), actor.user()));
        products.flush();
        return ProductResponse.from(product);
    }

    private Product require(UUID productId, Long organizationId) {
        return products.findByPublicIdAndOrganizationId(productId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("상품을 찾을 수 없습니다."));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

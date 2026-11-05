package com.tradeoperationsplatform.apiserver.domain.product.dto;

import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(UUID productId, String name, String hypothesis, String internalSku,
                              String brand, String category, Product.Status status, long version,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getPublicId(), product.getName(), product.getHypothesis(),
                product.getInternalSku(), product.getBrand(), product.getCategory(), product.getStatus(),
                product.getVersion(), product.getCreatedAt(), product.getUpdatedAt());
    }
}

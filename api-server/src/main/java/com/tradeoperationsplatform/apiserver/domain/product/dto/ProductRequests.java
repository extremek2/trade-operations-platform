package com.tradeoperationsplatform.apiserver.domain.product.dto;

import com.tradeoperationsplatform.apiserver.domain.product.entity.Product;
import jakarta.validation.constraints.*;

public final class ProductRequests {
    private ProductRequests() {}

    public record Create(@NotBlank @Size(max=300) String name, @Size(max=2000) String hypothesis,
                         @Size(max=100) String internalSku, @Size(max=200) String brand,
                         @Size(max=200) String category) {}
    public record Revise(@NotBlank @Size(max=300) String name, @Size(max=2000) String hypothesis,
                         @Size(max=100) String internalSku, @Size(max=200) String brand,
                         @Size(max=200) String category, @NotNull Long version) {}
    public record ChangeStatus(@NotNull Product.Status status, @NotBlank @Size(max=500) String reason,
                               @NotNull Long version) {}
}

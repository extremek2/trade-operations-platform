package com.tradeoperationsplatform.apiserver.domain.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public final class PurchaseSelectionRequests {
    private PurchaseSelectionRequests() {}

    public record Create(@NotNull Long version, @NotEmpty @Size(max = 200) List<@Valid Line> lines) {}
    public record Line(@Positive int lineNumber,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal desiredQuantity,
                       UUID existingProductId) {}
}

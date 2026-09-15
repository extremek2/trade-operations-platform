package com.tradeoperationsplatform.apiserver.domain.offer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.*;

public final class SupplierOfferRequests {
    private SupplierOfferRequests() {}

    public record Create(@NotNull UUID supplierId, @Size(max = 2000) String sourceReference,
                         @NotBlank @Size(max = 65536) String originalText,
                         @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency,
                         @NotEmpty @Size(max = 200) List<@Valid OriginalLine> lines) {}

    public record OriginalLine(@NotBlank @Size(max = 500) String originalName,
                               @Size(max = 200) String sourceLocation) {}

    public record ReviewLine(@NotNull Long version, @NotBlank @Size(max = 500) String reviewedName,
                             @Size(max = 100) String supplierSku, @Size(max = 30) String quantityUnit,
                             @DecimalMin(value = "0", inclusive = false) BigDecimal minimumQuantity,
                             @DecimalMin("0") BigDecimal unitPrice,
                             @Pattern(regexp = "[A-Za-z]{2}") String countryOfOrigin,
                             @Size(max = 1000) String notes) {}

    public record Action(@NotNull Long version) {}
}

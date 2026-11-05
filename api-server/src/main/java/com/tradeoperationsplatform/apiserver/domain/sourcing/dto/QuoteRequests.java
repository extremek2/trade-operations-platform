package com.tradeoperationsplatform.apiserver.domain.sourcing.dto;

import com.tradeoperationsplatform.apiserver.domain.sourcing.entity.SupplierQuote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public final class QuoteRequests {
    private QuoteRequests() {}

    public record Create(@NotNull UUID supplierId, @Size(max=100) String quoteNumber,
                         @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                         LocalDate quotedAt, LocalDate validUntil, @Size(max=2000) String notes,
                         @NotEmpty List<@Valid Line> lines) {}
    public record Line(@NotNull UUID productId, @Size(max=100) String supplierSku,
                       @NotBlank @Size(max=500) String description,
                       @DecimalMin(value="0", inclusive=false) BigDecimal minimumQuantity,
                       @Size(max=30) String quantityUnit,
                       @DecimalMin(value="0") BigDecimal unitPrice,
                       @Pattern(regexp="[A-Za-z]{2}") String countryOfOrigin,
                       @PositiveOrZero Integer leadTimeDays, @Size(max=1000) String notes) {}
    public record Revise(@Size(max=100) String quoteNumber,
                         @NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                         LocalDate quotedAt, LocalDate validUntil, @Size(max=2000) String notes,
                         @NotEmpty List<@Valid Line> lines, @NotNull Long version) {}
    public record CreateRevision(@NotBlank @Pattern(regexp="[A-Za-z]{3}") String currency,
                                 LocalDate quotedAt, LocalDate validUntil, @Size(max=2000) String notes,
                                 @NotEmpty List<@Valid Line> lines, @NotNull Long version) {}
    public record ChangeStatus(@NotNull SupplierQuote.Status status, @NotNull Long version) {}
}

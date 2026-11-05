package com.tradeoperationsplatform.apiserver.domain.onboarding.dto;
import jakarta.validation.constraints.*;
public record DecisionRequest(@NotNull @PositiveOrZero Long version,
                              @NotBlank @Size(max=2000) String reason, @Size(max=4000) String internalNote) {}

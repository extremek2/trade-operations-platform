package com.tradeoperationsplatform.apiserver.domain.onboarding.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ApplicationRequest(@NotBlank @Size(max=200) String organizationName,
                                 @Size(max=100) String businessNumber, @Size(max=100) String phone,
                                 UUID previousApplicationId) {}

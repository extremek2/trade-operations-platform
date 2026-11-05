package com.tradeoperationsplatform.apiserver.domain.identity.dto;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import jakarta.validation.constraints.*;

public record CreateOrganizationRequest(
        @NotBlank String name,
        @NotNull Organization.Type organizationType,
        String businessNumber,
        @Email String email,
        String phone,
        @NotBlank @Email String ownerEmail,
        @NotBlank String ownerName,
        String ownerPhone
) {}

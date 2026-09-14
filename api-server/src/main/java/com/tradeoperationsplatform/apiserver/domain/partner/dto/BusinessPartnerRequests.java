package com.tradeoperationsplatform.apiserver.domain.partner.dto;

import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner.Role;
import jakarta.validation.constraints.*;
import java.util.Set;

public final class BusinessPartnerRequests {
    private BusinessPartnerRequests() {}

    public record Create(@NotBlank @Size(max=200) String name,
                         @Pattern(regexp="[A-Za-z]{2}") String countryCode,
                         @Size(max=100) String businessNumber,
                         @NotEmpty Set<@NotNull Role> roles) {}
}

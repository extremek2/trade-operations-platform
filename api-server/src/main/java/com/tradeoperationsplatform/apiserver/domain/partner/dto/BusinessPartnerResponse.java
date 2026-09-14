package com.tradeoperationsplatform.apiserver.domain.partner.dto;

import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import java.time.LocalDateTime;
import java.util.*;

public record BusinessPartnerResponse(UUID businessPartnerId, String name, String countryCode,
                                      String businessNumber, BusinessPartner.Status status,
                                      Set<BusinessPartner.Role> roles, LocalDateTime createdAt,
                                      LocalDateTime updatedAt) {
    public static BusinessPartnerResponse from(BusinessPartner partner) {
        Set<BusinessPartner.Role> roles = partner.getRoles().isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(partner.getRoles()));
        return new BusinessPartnerResponse(partner.getPublicId(), partner.getName(), partner.getCountryCode(),
                partner.getBusinessNumber(), partner.getStatus(), roles, partner.getCreatedAt(), partner.getUpdatedAt());
    }
}

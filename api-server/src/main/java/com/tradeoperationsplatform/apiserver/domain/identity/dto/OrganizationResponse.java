package com.tradeoperationsplatform.apiserver.domain.identity.dto;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import java.util.UUID;

public record OrganizationResponse(
        UUID organizationId, String name, Organization.Type organizationType,
        Organization.Status status, UUID ownerUserId, String ownerEmail, AppUser.Status ownerStatus
) {
    public static OrganizationResponse of(Organization organization, AppUser owner) {
        return new OrganizationResponse(organization.getPublicId(), organization.getName(),
                organization.getOrganizationType(), organization.getStatus(), owner.getPublicId(),
                owner.getEmail(), owner.getStatus());
    }
}

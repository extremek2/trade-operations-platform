package com.tradeoperationsplatform.apiserver.domain.auth.dto;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember;
import java.util.UUID;
import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;

public record AuthResponse(String accessToken, long expiresInSeconds, UserContext user, String nextPath) {
    public record UserContext(UUID userId, String name, String email, UUID organizationId,
                              String organizationName, OrganizationMember.Role role, RefreshSession.Kind sessionKind, boolean systemAdmin, boolean emailVerified,
                              UUID shipmentId, UUID participantId, String caseAccessLevel) {}
}

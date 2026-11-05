package com.tradeoperationsplatform.apiserver.domain.auth.service;

import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import com.tradeoperationsplatform.apiserver.domain.auth.repository.RefreshSessionRepository;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.OrganizationMemberRepository;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.PlatformRoleAssignment;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.PlatformRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionAccessService {
    private final RefreshSessionRepository sessions;
    private final OrganizationMemberRepository members;
    private final PlatformRoleRepository platformRoles;
    private final com.tradeoperationsplatform.apiserver.domain.collaboration.CaseAccess caseAccess;

    @Transactional(readOnly = true)
    public Access authenticate(Jwt jwt) {
        try {
            UUID sessionId = UUID.fromString(jwt.getClaimAsString("session_id"));
            RefreshSession session = sessions.findByPublicId(sessionId).orElseThrow(SessionAccessService::invalid);
            if (!session.getUser().getPublicId().toString().equals(jwt.getSubject())) throw invalid();
            return validate(session);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw invalid();
        }
    }

    @Transactional(readOnly = true)
    public Access validate(RefreshSession session) {
        if (!session.isUsable(LocalDateTime.now()) || session.getUser().getStatus() != AppUser.Status.ACTIVE) throw invalid();
        OrganizationMember member = null;
        String authority = "ACCOUNT";
        if (session.getSessionKind() == RefreshSession.Kind.ORGANIZATION) {
            Organization organization = session.getOrganization();
            if (organization == null || organization.getStatus() != Organization.Status.ACTIVE) throw invalid();
            member = members.findByUserIdAndOrganizationIdAndStatus(session.getUser().getId(), organization.getId(), OrganizationMember.Status.ACTIVE)
                    .orElseThrow(SessionAccessService::invalid);
            authority = member.getMemberRole().name();
        } else if (session.getSessionKind() == RefreshSession.Kind.CASE) {
            authority = "CASE_" + caseAccess.require(session.getParticipantId(), session.getUser().getId()).accessLevel();
        } else if (session.getSessionKind() == RefreshSession.Kind.PLATFORM) {
            if (!platformRoles.existsByUserIdAndRoleAndActiveTrue(session.getUser().getId(), PlatformRoleAssignment.Role.SYSTEM_ADMIN)) throw invalid();
            authority = "SYSTEM_ADMIN";
        }
        return new Access(session, member, authority);
    }

    public static OAuth2AuthenticationException invalid() {
        return new OAuth2AuthenticationException(new OAuth2Error("invalid_token"), "세션이 만료되었거나 현재 접근 권한이 없습니다.");
    }

    public record Access(RefreshSession session, OrganizationMember member, String authority) {}
}

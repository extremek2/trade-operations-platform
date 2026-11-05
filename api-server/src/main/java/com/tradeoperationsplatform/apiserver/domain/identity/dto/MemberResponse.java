package com.tradeoperationsplatform.apiserver.domain.identity.dto;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember;
import java.time.LocalDateTime;
import java.util.UUID;

public record MemberResponse(UUID userId, String name, String email, OrganizationMember.Role role,
                             OrganizationMember.Status status, long version, LocalDateTime joinedAt) {
    public static MemberResponse from(OrganizationMember member) {
        return new MemberResponse(member.getUser().getPublicId(), member.getUser().getName(), member.getUser().getEmail(),
                member.getMemberRole(), member.getStatus(), member.getVersion(), member.getJoinedAt());
    }
}

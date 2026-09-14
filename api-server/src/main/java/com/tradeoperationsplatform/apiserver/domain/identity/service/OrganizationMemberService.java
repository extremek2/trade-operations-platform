package com.tradeoperationsplatform.apiserver.domain.identity.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.auth.repository.RefreshSessionRepository;
import com.tradeoperationsplatform.apiserver.domain.identity.dto.*;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember.Role.*;
import static com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember.Status.*;

@Service
@RequiredArgsConstructor
public class OrganizationMemberService {
    private final CurrentActor currentActor;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final AppUserRepository users;
    private final IdentityAuditRepository audit;
    private final RefreshSessionRepository sessions;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<MemberResponse> list(int page, int size) {
        var actor = currentActor.require();
        requireManager(actor.member());
        return members.findAllByOrganizationId(actor.organization().getId(),
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by("id"))).map(MemberResponse::from);
    }

    @Transactional
    public MemberResponse add(AddMemberRequest request) {
        var actor = lockManager();
        requireAssignable(actor.member(), request.role());
        var user = users.findByEmailIgnoreCase(request.email().trim())
                .filter(u -> u.getStatus() == AppUser.Status.ACTIVE && u.getEmailVerifiedAt() != null)
                .orElseThrow(() -> new BusinessException("가입과 이메일 확인을 완료한 활성 계정만 추가할 수 있습니다."));
        users.lockById(user.getId()).orElseThrow();
        entityManager.refresh(user);
        if (user.getStatus() != AppUser.Status.ACTIVE || user.getEmailVerifiedAt() == null)
            throw new BusinessException("가입과 이메일 확인을 완료한 활성 계정만 추가할 수 있습니다.");
        if (members.findByOrganizationIdAndUserPublicId(actor.organization().getId(), user.getPublicId()).isPresent())
            throw new ConflictException("이미 등록된 직원입니다. 비활성 직원은 목록에서 활성화해 주세요.");
        requireSingleOrganization(user, actor.organization());
        var member = members.saveAndFlush(new OrganizationMember(actor.organization(), user, request.role()));
        audit.save(IdentityAuditEvent.membership(actor.user(), member, null, request.reason().trim()));
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse change(UUID userId, ChangeMemberRequest request) {
        var actor = lockManager();
        var member = members.findByOrganizationIdAndUserPublicId(actor.organization().getId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("조직 직원을 찾을 수 없습니다."));
        entityManager.refresh(member);
        if (member.getUser().getId().equals(actor.user().getId()) || member.getMemberRole() == OWNER)
            throw new AccessDeniedException("본인 또는 OWNER의 권한은 변경할 수 없습니다.");
        requireAssignable(actor.member(), member.getMemberRole());
        requireAssignable(actor.member(), request.role());
        if (member.getVersion() != request.version()) throw new ConflictException("직원 정보가 변경되었습니다. 최신 목록을 확인해 주세요.");
        if (member.getStatus() == request.status() && member.getMemberRole() == request.role())
            throw new BusinessException("변경할 권한 또는 상태를 선택해 주세요.");
        if (request.status() == ACTIVE && member.getUser().getStatus() != AppUser.Status.ACTIVE)
            throw new BusinessException("비활성 계정은 직원으로 활성화할 수 없습니다.");
        if (member.getStatus() == INACTIVE && request.status() == ACTIVE) {
            users.lockById(member.getUser().getId()).orElseThrow();
            entityManager.refresh(member.getUser());
            if (member.getUser().getStatus() != AppUser.Status.ACTIVE)
                throw new BusinessException("비활성 계정은 직원으로 활성화할 수 없습니다.");
            requireSingleOrganization(member.getUser(), actor.organization());
        }
        String before = member.getMemberRole().name() + ":" + member.getStatus().name();
        member.change(request.role(), request.status());
        members.flush();
        if (request.status() == INACTIVE)
            sessions.revokeOrganizationSessions(member.getUser().getId(), actor.organization().getId(), LocalDateTime.now());
        audit.save(IdentityAuditEvent.membership(actor.user(), member, before, request.reason().trim()));
        return MemberResponse.from(member);
    }

    private CurrentActor.Context lockManager() {
        var actor = currentActor.require();
        organizations.lockById(actor.organization().getId()).orElseThrow();
        // Authentication may have loaded the actor before another manager changed their role.
        entityManager.refresh(actor.member());
        entityManager.refresh(actor.organization());
        entityManager.refresh(actor.user());
        if (actor.organization().getStatus() != Organization.Status.ACTIVE || actor.user().getStatus() != AppUser.Status.ACTIVE)
            throw new AccessDeniedException("활성 조직과 계정이 필요합니다.");
        requireManager(actor.member());
        return actor;
    }

    private void requireManager(OrganizationMember member) {
        if (member.getStatus() != ACTIVE || (member.getMemberRole() != OWNER && member.getMemberRole() != ADMIN))
            throw new AccessDeniedException("직원 관리 권한이 필요합니다.");
    }

    private void requireSingleOrganization(AppUser user, Organization organization) {
        if (members.findAllByUserIdAndStatus(user.getId(), ACTIVE).stream()
                .anyMatch(m -> !m.getOrganization().getId().equals(organization.getId())))
            throw new ConflictException("다른 조직의 활성 직원은 추가할 수 없습니다. 기존 소속을 먼저 정리해 주세요.");
    }

    private void requireAssignable(OrganizationMember actor, OrganizationMember.Role role) {
        if (role == OWNER || (actor.getMemberRole() == ADMIN && role == ADMIN))
            throw new AccessDeniedException("해당 역할은 관리할 수 없습니다.");
    }
}

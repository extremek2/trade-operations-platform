package com.tradeoperationsplatform.apiserver.domain.auth.service;

import com.tradeoperationsplatform.apiserver.domain.auth.dto.*;
import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import com.tradeoperationsplatform.apiserver.domain.auth.repository.RefreshSessionRepository;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.AuthenticationFailedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final AppUserRepository userRepository;
    private final com.tradeoperationsplatform.apiserver.domain.onboarding.service.OrganizationApplicationService applications;
    private final com.tradeoperationsplatform.apiserver.domain.onboarding.service.EmailVerificationService verification;
    private final OrganizationMemberRepository memberRepository; private final RefreshSessionRepository refreshRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private final SessionAccessService sessionAccess;
    private final com.tradeoperationsplatform.apiserver.domain.collaboration.CaseAccess caseAccess;
    private final com.tradeoperationsplatform.apiserver.domain.platform.repository.PlatformRoleRepository platformRoles;
    private final PasswordEncoder passwordEncoder; private final TokenService tokenService; private final CurrentActor currentActor;
    @Value("${app.auth.refresh-token-days}") private long refreshDays;

    @Transactional
    public Tokens signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(email)) throw new com.tradeoperationsplatform.apiserver.global.exception.ConflictException("이미 등록된 이메일입니다. 로그인 후 신청해 주세요.");
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new BusinessException("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        AppUser user = userRepository.save(AppUser.registered(email, passwordEncoder.encode(request.password()), request.name(), request.phone()));
        applications.submitFor(user, new com.tradeoperationsplatform.apiserver.domain.onboarding.dto.ApplicationRequest(request.organizationName(), request.businessNumber(), request.phone(), null));
        verification.issueFor(user);
        return issue(user, null, RefreshSession.Kind.ACCOUNT);
    }

    @Transactional
    public Tokens login(LoginRequest request) {
        AppUser user = authenticatePassword(request);
        List<OrganizationMember> memberships = memberRepository.findAllByUserIdAndStatus(user.getId(), OrganizationMember.Status.ACTIVE);
        user.recordLogin();
        if (memberships.isEmpty() && request.organizationId() == null) return issue(user, null, RefreshSession.Kind.ACCOUNT);
        OrganizationMember member = selectMembership(memberships, request.organizationId());
        if (member.getOrganization().getStatus() != Organization.Status.ACTIVE) throw new BusinessException("비활성 조직입니다.");
        return issue(user, member.getOrganization(), RefreshSession.Kind.ORGANIZATION);
    }

    @Transactional
    public Tokens platformLogin(LoginRequest request) {
        AppUser user = authenticatePassword(request);
        requirePlatformRole(user);
        user.recordLogin();
        return issue(user, null, RefreshSession.Kind.PLATFORM);
    }

    private AppUser authenticatePassword(LoginRequest request) {
        AppUser user = userRepository.findByEmailIgnoreCase(request.email().trim()).orElseThrow(() -> new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (user.getStatus() != AppUser.Status.ACTIVE || user.getPasswordHash() == null || !passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new AuthenticationFailedException("이메일 또는 비밀번호가 올바르지 않습니다.");
        return user;
    }

    @Transactional
    public Tokens switchContext(ContextRequest request) {
        if (request.kind() == RefreshSession.Kind.CASE) throw new BusinessException("건별 이메일 접속 링크를 이용해 주세요.");
        var authenticated = currentActor.access();
        var lockedSession = refreshRepository.findLockedByPublicId(authenticated.session().getPublicId())
                .orElseThrow(SessionAccessService::invalid);
        entityManager.refresh(lockedSession);
        var current = sessionAccess.validate(lockedSession);
        AppUser user = current.session().getUser();
        Organization organization = null;
        if (request.kind() == RefreshSession.Kind.ORGANIZATION) {
            if (request.organizationId() == null) throw new BusinessException("조직을 선택해 주세요.");
            var memberships = memberRepository.findAllByUserIdAndStatus(user.getId(), OrganizationMember.Status.ACTIVE);
            organization = selectMembership(memberships, request.organizationId()).getOrganization();
            if (organization.getStatus() != Organization.Status.ACTIVE) throw new BusinessException("비활성 조직입니다.");
        } else {
            if (request.organizationId() != null) throw new BusinessException("조직 없는 컨텍스트에는 조직 ID를 지정할 수 없습니다.");
            if (request.kind() == RefreshSession.Kind.PLATFORM) requirePlatformRole(user);
        }
        current.session().revoke();
        return issue(user, organization, request.kind());
    }

    private void requirePlatformRole(AppUser user) {
        if (!platformRoles.existsByUserIdAndRoleAndActiveTrue(user.getId(), com.tradeoperationsplatform.apiserver.domain.platform.entity.PlatformRoleAssignment.Role.SYSTEM_ADMIN))
            throw new org.springframework.security.access.AccessDeniedException("시스템관리자 권한이 필요합니다.");
    }

    @Transactional
    public Tokens refresh(String rawToken) {
        RefreshSession session = refreshRepository.findLockedByTokenHash(tokenService.hash(rawToken))
                .orElseThrow(() -> new AuthenticationFailedException("유효하지 않은 Refresh Token입니다."));
        var access = sessionAccess.validate(session);
        String refresh = tokenService.newRefreshToken();
        session.rotate(tokenService.hash(refresh));
        return tokens(session, access.member(), access.authority(), refresh);
    }

    @Transactional
    public void logout(String rawToken) {
        refreshRepository.findLockedByTokenHash(tokenService.hash(rawToken)).ifPresent(RefreshSession::revoke);
    }

    @Transactional(readOnly = true)
    public AuthResponse.UserContext me() {
        var access = currentActor.access();
        return context(access.session(), access.member());
    }

    private OrganizationMember selectMembership(List<OrganizationMember> memberships, UUID organizationId) {
        if (memberships.isEmpty()) throw new BusinessException("활성 조직 소속이 없습니다.");
        if (organizationId == null && memberships.size() == 1) return memberships.get(0);
        if (organizationId == null) throw new BusinessException("로그인할 조직을 선택해 주세요.");
        return memberships.stream().filter(m -> m.getOrganization().getPublicId().equals(organizationId)).findFirst()
                .orElseThrow(() -> new BusinessException("선택한 조직에 소속되어 있지 않습니다."));
    }

    private Tokens issue(AppUser user, Organization organization, RefreshSession.Kind kind) {
        String refresh = tokenService.newRefreshToken();
        RefreshSession session = refreshRepository.save(new RefreshSession(user, organization, kind,
                tokenService.hash(refresh), LocalDateTime.now().plusDays(refreshDays)));
        var access = sessionAccess.validate(session);
        return tokens(session, access.member(), access.authority(), refresh);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Tokens issueCase(AppUser user, Long participantId) {
        String raw = tokenService.newRefreshToken();
        var session = refreshRepository.save(RefreshSession.forCase(user, participantId, tokenService.hash(raw), LocalDateTime.now().plusDays(refreshDays)));
        var access = sessionAccess.validate(session);
        return tokens(session, null, access.authority(), raw);
    }

    private Tokens tokens(RefreshSession session, OrganizationMember member, String authority, String refresh) {
        return new Tokens(new AuthResponse(tokenService.createAccessToken(session, authority), tokenService.accessTokenSeconds(), context(session, member), switch (session.getSessionKind()) {
            case ACCOUNT -> "/application"; case ORGANIZATION -> "/"; case PLATFORM -> "/system-admin/applications"; case CASE -> "/external-case";
        }), refresh);
    }

    private AuthResponse.UserContext context(RefreshSession session, OrganizationMember member) {
        AppUser user = session.getUser();
        Organization organization = session.getOrganization();
        var scope = session.getSessionKind() == RefreshSession.Kind.CASE ? caseAccess.require(session.getParticipantId(), user.getId()) : null;
        return new AuthResponse.UserContext(user.getPublicId(), user.getName(), user.getEmail(),
                organization == null ? null : organization.getPublicId(), organization == null ? null : organization.getName(),
                member == null ? null : member.getMemberRole(), session.getSessionKind(), session.getSessionKind() == RefreshSession.Kind.PLATFORM, user.getEmailVerifiedAt() != null,
                scope == null ? null : scope.shipmentPublicId(), scope == null ? null : scope.participantPublicId(), scope == null ? null : scope.accessLevel());
    }
    public record Tokens(AuthResponse response, String refreshToken) {}
}

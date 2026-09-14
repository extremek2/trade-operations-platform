package com.tradeoperationsplatform.apiserver.domain.auth.service;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CurrentActor {
    private final SessionAccessService accessService;

    @Transactional(readOnly = true)
    public SessionAccessService.Access access() {
        if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken authentication)) {
            throw SessionAccessService.invalid();
        }
        return accessService.authenticate(authentication.getToken());
    }

    @Transactional(readOnly = true)
    public Context require() {
        var access = access();
        if (access.member() == null) throw new AccessDeniedException("조직 업무 권한이 필요합니다.");
        return new Context(access.session().getUser(), access.session().getOrganization(), access.member());
    }
    public record Context(AppUser user, Organization organization, OrganizationMember member) {}
}

package com.tradeoperationsplatform.apiserver.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final SessionAccessService accessService;
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        var access = accessService.authenticate(jwt);
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + access.authority())));
    }
}

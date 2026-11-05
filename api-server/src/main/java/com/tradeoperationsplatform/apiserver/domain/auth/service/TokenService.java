package com.tradeoperationsplatform.apiserver.domain.auth.service;

import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${app.auth.access-token-minutes}") private long accessMinutes;

    public String createAccessToken(RefreshSession session, String authority) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("trade-ops-api").issuedAt(now)
                .expiresAt(now.plus(Duration.ofMinutes(accessMinutes))).subject(session.getUser().getPublicId().toString())
                .claim("session_id", session.getPublicId().toString())
                .claim("session_kind", session.getSessionKind().name()).claim("role", authority);
        if (session.getOrganization() != null) claims.claim("organizationId", session.getOrganization().getPublicId().toString());
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build())).getTokenValue();
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[32]; secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public long accessTokenSeconds() { return accessMinutes * 60; }

    public String hash(String token) {
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}

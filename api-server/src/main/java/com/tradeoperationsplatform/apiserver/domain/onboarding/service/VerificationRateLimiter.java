package com.tradeoperationsplatform.apiserver.domain.onboarding.service;
import com.tradeoperationsplatform.apiserver.domain.auth.service.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class VerificationRateLimiter {
    private final JdbcTemplate jdbc;
    private final TokenService tokens;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean allowed(String remoteAddress) {
        jdbc.update("DELETE FROM auth_rate_limit WHERE window_start<NOW()-INTERVAL '1 hour'");
        int count = jdbc.queryForObject("INSERT INTO auth_rate_limit(bucket_key,window_start,requests) VALUES (?,date_trunc('minute',NOW()),1) ON CONFLICT(bucket_key,window_start) DO UPDATE SET requests=auth_rate_limit.requests+1 RETURNING requests", Integer.class, tokens.hash(remoteAddress));
        return count <= 30;
    }
}

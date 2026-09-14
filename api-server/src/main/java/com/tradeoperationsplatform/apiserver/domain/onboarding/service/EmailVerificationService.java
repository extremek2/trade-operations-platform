package com.tradeoperationsplatform.apiserver.domain.onboarding.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.*;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.AppUserRepository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.repository.ApplicationRepository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication.Status;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.*;

@Service
@RequiredArgsConstructor
public class EmailVerificationService {
    private final AppUserRepository users;
    private final ApplicationRepository applications;
    private final TokenService tokens;
    private final CurrentActor actor;
    private final JdbcTemplate jdbc;
    private final MailOutbox outbox;
    private final EntityManager entityManager;
    private final IdentityAuditRepository audit;
    @Value("${app.mail.verification-url:http://localhost:3000/verify-email}") private String verificationUrl;

    @Transactional
    public void request() { issueFor(actor.access().session().getUser()); }

    @Transactional(propagation = Propagation.MANDATORY)
    public void issueFor(AppUser target) {
        AppUser user = users.lockById(target.getId()).orElseThrow();
        entityManager.refresh(user);
        if (user.getStatus() != AppUser.Status.ACTIVE) throw new BusinessException("이메일 확인을 진행할 수 없습니다.");
        if (user.getEmailVerifiedAt() != null) return;
        long recent = jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE user_id=? AND created_at>NOW()-INTERVAL '1 minute'", Long.class, user.getId());
        long hourly = jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE user_id=? AND created_at>NOW()-INTERVAL '1 hour'", Long.class, user.getId());
        if (recent > 0 || hourly >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 요청해 주세요.");
        URI base = URI.create(verificationUrl);
        if (base.getHost() == null || base.getRawQuery() != null || base.getRawFragment() != null
                || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && "localhost".equals(base.getHost()))))
            throw new IllegalStateException("Verification URL must be an HTTPS URL without query or fragment (localhost HTTP allowed)");
        jdbc.update("UPDATE email_auth_token SET revoked_at=NOW() WHERE user_id=? AND consumed_at IS NULL AND revoked_at IS NULL", user.getId());
        jdbc.update("UPDATE mail_outbox SET status='CANCELLED', encrypted_payload=NULL WHERE status='PENDING' AND email_token_id IN (SELECT id FROM email_auth_token WHERE user_id=? AND revoked_at IS NOT NULL)", user.getId());
        String raw = tokens.newRefreshToken();
        LocalDateTime expiry = LocalDateTime.now().plusMinutes(15);
        Long id = jdbc.queryForObject("INSERT INTO email_auth_token(user_id,purpose,target_email,token_hash,expires_at) VALUES (?,'VERIFY_EMAIL',?,?,?) RETURNING id", Long.class, user.getId(), user.getEmail(), tokens.hash(raw), expiry);
        // Fragment keeps the bearer token out of HTTP request URLs and referrers.
        outbox.enqueue(id, new MailMessage(user.getEmail(), "이메일 주소 확인", "다음 링크에서 이메일 확인을 완료해 주세요. 링크는 15분 동안 유효합니다.\n" + verificationUrl + "#token=" + raw), expiry);
    }

    @Transactional
    public void confirm(String raw) {
        String hash = tokens.hash(raw);
        var lookup = jdbc.queryForList("SELECT user_id FROM email_auth_token WHERE token_hash=? AND purpose='VERIFY_EMAIL'", hash);
        if (lookup.isEmpty()) throw invalid();
        AppUser user = users.lockById(((Number)lookup.get(0).get("user_id")).longValue()).orElseThrow(EmailVerificationService::invalid);
        entityManager.refresh(user);
        if (user.getStatus() != AppUser.Status.ACTIVE) throw invalid();
        var ids = jdbc.queryForList("UPDATE email_auth_token SET consumed_at=NOW() WHERE token_hash=? AND user_id=? AND target_email=? AND purpose='VERIFY_EMAIL' AND expires_at>NOW() AND consumed_at IS NULL AND revoked_at IS NULL RETURNING id", hash, user.getId(), user.getEmail());
        if (ids.isEmpty()) throw invalid();
        markVerified(user);
        jdbc.update("UPDATE mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE status='PENDING' AND email_token_id=?", ids.get(0).get("id"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markVerified(AppUser user) {
        user.verifyEmail();
        var pending = applications.findByApplicantIdAndStatusIn(user.getId(), List.of(Status.PENDING_EMAIL));
        for (var application : pending) {
            application.emailVerified();
            audit.save(IdentityAuditEvent.application(user, application.getPublicId(), Status.PENDING_EMAIL.name(), Status.PENDING_REVIEW.name(), "이메일 확인 완료", null));
        }
    }
    private static BusinessException invalid() { return new BusinessException("유효하지 않거나 만료된 확인 링크입니다."); }
}

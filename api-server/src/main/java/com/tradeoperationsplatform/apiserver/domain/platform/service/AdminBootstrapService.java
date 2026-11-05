package com.tradeoperationsplatform.apiserver.domain.platform.service;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.AppUserRepository;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.*;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminBootstrapService {
    private final AppUserRepository users;
    private final PlatformRoleRepository roles;
    private final IdentityAuditRepository audit;
    private final PasswordEncoder passwords;
    private final EntityManager entityManager;

    @Transactional
    public boolean createInitialAdmin(String email, String name, char[] password) {
        if (email == null || !email.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || name == null || name.isBlank())
            throw new IllegalArgumentException("유효한 이메일과 이름을 입력해 주세요.");
        if (password == null || password.length < 12 || new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("비밀번호는 12자 이상, UTF-8 기준 72바이트 이하여야 합니다.");
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        // Serialize explicit bootstrap attempts across processes, without granting a public endpoint.
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(728341906)").getSingleResult();
        var existing = users.findByEmailIgnoreCase(normalizedEmail);
        if (existing.isPresent()) {
            if (existing.get().getStatus() == AppUser.Status.ACTIVE && roles.existsByUserIdAndRoleAndActiveTrue(existing.get().getId(), PlatformRoleAssignment.Role.SYSTEM_ADMIN)) return false;
            throw new IllegalStateException("기존 계정은 초기화 명령으로 승격하거나 덮어쓸 수 없습니다.");
        }
        if (roles.count() != 0) throw new IllegalStateException("초기 시스템관리자가 이미 설정되어 있습니다. 추가·복구는 별도 운영 절차가 필요합니다.");
        AppUser user = users.save(AppUser.registered(normalizedEmail, passwords.encode(new String(password)), name.trim(), null));
        roles.save(new PlatformRoleAssignment(user));
        audit.save(IdentityAuditEvent.bootstrap(user));
        return true;
    }
}

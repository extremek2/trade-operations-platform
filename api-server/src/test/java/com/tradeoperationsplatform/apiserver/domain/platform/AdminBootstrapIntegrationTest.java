package com.tradeoperationsplatform.apiserver.domain.platform;

import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import com.tradeoperationsplatform.apiserver.domain.platform.service.AdminBootstrapService;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.*;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.AppUserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class AdminBootstrapIntegrationTest extends PostgresTestSupport {
    @Autowired AdminBootstrapService bootstrap;
    @Autowired AppUserRepository users;
    @Autowired PlatformRoleRepository roles;
    @MockitoSpyBean IdentityAuditRepository audit;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    final char[] password = "bootstrap-password-123".toCharArray();

    @Test void bootstrapIsExplicitIdempotentAndWritesAuditWithoutOrganization() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            roles.deleteAll();
            String email = UUID.randomUUID() + "@example.com";
            long audits = audit.count();
            assertThat(bootstrap.createInitialAdmin(email, "관리자", password)).isTrue();
            assertThat(bootstrap.createInitialAdmin(email, "관리자", password)).isFalse();
            var user = users.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(passwords.matches(new String(password), user.getPasswordHash())).isTrue();
            assertThat(roles.existsByUserIdAndRoleAndActiveTrue(user.getId(), PlatformRoleAssignment.Role.SYSTEM_ADMIN)).isTrue();
            assertThat(audit.count()).isEqualTo(audits + 1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?", Integer.class, user.getId())).isZero();
            status.setRollbackOnly();
        });
    }

    @Test void existingRegularAccountCannotBePromotedByBootstrap() {
        var user = users.save(AppUser.registered(UUID.randomUUID() + "@example.com", passwords.encode(new String(password)), "일반", null));
        assertThatThrownBy(() -> bootstrap.createInitialAdmin(user.getEmail(), "관리자", password)).isInstanceOf(IllegalStateException.class);
        assertThat(roles.existsByUserIdAndRole(user.getId(), PlatformRoleAssignment.Role.SYSTEM_ADMIN)).isFalse();
    }

    @Test void auditFailureRollsBackUserAndRoleCreation() {
        String email = UUID.randomUUID() + "@example.com";
        doThrow(new IllegalStateException("test audit failure")).when(audit).save(any(IdentityAuditEvent.class));
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(status -> {
            roles.deleteAll();
            return bootstrap.createInitialAdmin(email, "관리자", password);
        })).isInstanceOf(IllegalStateException.class).hasMessage("test audit failure");
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
    }
}

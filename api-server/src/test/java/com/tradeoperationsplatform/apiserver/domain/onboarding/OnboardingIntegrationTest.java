package com.tradeoperationsplatform.apiserver.domain.onboarding;

import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import com.tradeoperationsplatform.apiserver.domain.auth.dto.*;
import com.tradeoperationsplatform.apiserver.domain.auth.service.*;
import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.PlatformRoleAssignment;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.PlatformRoleRepository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.dto.*;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication;
import com.tradeoperationsplatform.apiserver.domain.onboarding.repository.ApplicationRepository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.service.*;
import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OnboardingIntegrationTest extends PostgresTestSupport {
    @Autowired AuthService auth;
    @Autowired OrganizationApplicationService applications;
    @Autowired EmailVerificationService verification;
    @Autowired ApplicationRepository repository;
    @Autowired AppUserRepository users;
    @Autowired PlatformRoleRepository roles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired OutboxCipher cipher;
    @Autowired ObjectMapper mapper;
    @Autowired JwtDecoder decoder;
    @Autowired SessionJwtAuthenticationConverter converter;
    @Autowired MockMvc mvc;
    @MockitoSpyBean MailOutbox outbox;
    static final String PASSWORD = "test-password-1234";

    AuthService.Tokens signup() {
        return auth.signup(new SignupRequest("신청 회사", "BUS-"+UUID.randomUUID(), "신청자", UUID.randomUUID()+"@example.com", PASSWORD, "010-1234-5678"));
    }
    AuthService.Tokens admin() {
        var user = users.save(AppUser.registered(UUID.randomUUID()+"@example.com", passwords.encode(PASSWORD), "운영자", null));
        roles.save(new PlatformRoleAssignment(user));
        return auth.platformLogin(new LoginRequest(user.getEmail(), PASSWORD, null));
    }
    <T> T as(AuthService.Tokens tokens, Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(converter.convert(decoder.decode(tokens.response().accessToken())));
        SecurityContextHolder.setContext(context);
        try { return operation.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    Long userId(AuthService.Tokens tokens) { return users.findByPublicId(tokens.response().user().userId()).orElseThrow().getId(); }
    OrganizationApplication application(AuthService.Tokens tokens) { return repository.findFirstByApplicantIdOrderByIdDesc(userId(tokens)).orElseThrow(); }
    String token(AuthService.Tokens tokens) {
        String payload = jdbc.queryForObject("SELECT o.encrypted_payload FROM mail_outbox o JOIN email_auth_token t ON o.email_token_id=t.id WHERE t.user_id=? ORDER BY t.id DESC LIMIT 1", String.class, userId(tokens));
        try { return mapper.readValue(cipher.decrypt(payload), MailMessage.class).body().split("#token=")[1]; }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    OrganizationApplication verified(AuthService.Tokens tokens) { verification.confirm(token(tokens)); return application(tokens); }
    DecisionRequest decision(OrganizationApplication application) { return new DecisionRequest(application.getVersion(), "신청 정보 확인", "운영자 내부 메모"); }

    @Test void applicantAddedAsEmployeeCannotAlsoBeApprovedAsNewOwner() throws Exception {
        var applicant=signup(); var application=verified(applicant); var reviewer=admin();
        var org=jdbc.queryForObject("insert into organization(public_id,name,organization_type,status,created_at,updated_at) values (?,?,'SHIPPER','ACTIVE',now(),now()) returning id", Long.class, UUID.randomUUID(), "직원 소속");
        jdbc.update("insert into organization_member(organization_id,user_id,member_role,status,joined_at) values (?,?,'OPERATOR','ACTIVE',now())", org,userId(applicant));
        assertThatThrownBy(() -> as(reviewer,() -> applications.decide(application.getPublicId(),decision(application),true))).isInstanceOf(ConflictException.class);
        assertThat(application(applicant).getStatus()).isEqualTo(OrganizationApplication.Status.PENDING_REVIEW);
        assertThat(jdbc.queryForObject("select count(*) from organization_member where user_id=?",Integer.class,userId(applicant))).isEqualTo(1);
    }

    @Test void signupCreatesPendingApplicationAndNeverCreatesOrganizationOrOwner() throws Exception {
        long organizations = jdbc.queryForObject("SELECT count(*) FROM organization", Long.class);
        var tokens = signup();
        assertThat(tokens.response().user().sessionKind()).isEqualTo(RefreshSession.Kind.ACCOUNT);
        assertThat(tokens.response().user().organizationId()).isNull();
        assertThat(tokens.response().user().role()).isNull();
        assertThat(tokens.response().user().emailVerified()).isFalse();
        assertThat(tokens.response().nextPath()).isEqualTo("/application");
        assertThat(application(tokens).getStatus()).isEqualTo(OrganizationApplication.Status.PENDING_EMAIL);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization", Long.class)).isEqualTo(organizations);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?", Long.class, userId(tokens))).isZero();
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer "+tokens.response().accessToken())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer "+tokens.response().accessToken()).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void verificationIsSingleUseAndDoesNotApproveOrganization() {
        var applicant = signup(); String raw = token(applicant);
        verification.confirm(raw);
        assertThat(application(applicant).getStatus()).isEqualTo(OrganizationApplication.Status.PENDING_REVIEW);
        assertThat(users.findByPublicId(applicant.response().user().userId()).orElseThrow().getEmailVerifiedAt()).isNotNull();
        assertThatThrownBy(() -> verification.confirm(raw)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?", Integer.class, userId(applicant))).isZero();
    }

    @Test void administratorCannotApproveUnverifiedApplication() {
        var applicant = signup(); var administrator = admin(); var application = application(applicant);
        assertThatThrownBy(() -> as(administrator, () -> applications.decide(application.getPublicId(), decision(application), true))).isInstanceOf(ConflictException.class);
    }

    @Test void approvalCreatesOwnerAndUserMustSelectNewOrganizationContext() throws Exception {
        var applicant = signup(); var administrator = admin(); var application = verified(applicant);
        var result = as(administrator, () -> applications.decide(application.getPublicId(), decision(application), true));
        assertThat(result.application().status()).isEqualTo(OrganizationApplication.Status.APPROVED);
        assertThat(result.application().version()).isGreaterThan(application.getVersion());
        assertThat(result.application().approvedOrganizationId()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT member_role FROM organization_member WHERE user_id=?", String.class, userId(applicant))).isEqualTo("OWNER");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_audit_event WHERE target_id=? AND action='APPLICATION_APPROVED'", Integer.class, application.getPublicId().toString())).isEqualTo(1);
        assertThat(auth.refresh(applicant.refreshToken()).response().user().sessionKind()).isEqualTo(RefreshSession.Kind.ACCOUNT);
        var organizationSession = as(applicant, () -> auth.switchContext(new ContextRequest(RefreshSession.Kind.ORGANIZATION, result.application().approvedOrganizationId())));
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer "+organizationSession.response().accessToken())).andExpect(status().isOk());
    }

    @Test void rejectionPreservesOriginalAndReapplicationIsSeparateSnapshot() {
        var applicant = signup(); var administrator = admin(); var original = verified(applicant);
        as(administrator, () -> applications.decide(original.getPublicId(), new DecisionRequest(original.getVersion(), "회사 정보를 보완해 주세요", "내부 전용"), false));
        var retry = as(applicant, () -> applications.submit(new ApplicationRequest("수정 회사", "NEW-BUS", null, original.getPublicId())));
        assertThat(retry.applicationId()).isNotEqualTo(original.getPublicId());
        assertThat(retry.previousApplicationId()).isEqualTo(original.getPublicId());
        assertThat(retry.status()).isEqualTo(OrganizationApplication.Status.PENDING_REVIEW);
        var preserved = repository.findByPublicId(original.getPublicId()).orElseThrow();
        assertThat(preserved.getOrganizationName()).isEqualTo("신청 회사");
        assertThat(preserved.getPublicReason()).isEqualTo("회사 정보를 보완해 주세요");
    }

    @Test void applicantCanOnlyListOwnApplicationsWithoutInternalNotes() throws Exception {
        var applicant = signup(); var other = signup(); var administrator = admin(); var application = verified(applicant);
        as(administrator, () -> applications.decide(application.getPublicId(), decision(application), false));
        mvc.perform(get("/api/v1/me/applications").header("Authorization", "Bearer "+applicant.response().accessToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].applicationId").value(application.getPublicId().toString()))
                .andExpect(jsonPath("$.data.content[0].internalNote").doesNotExist());
        mvc.perform(get("/api/v1/system-admin/applications/"+application(other).getPublicId()).header("Authorization", "Bearer "+applicant.response().accessToken()))
                .andExpect(status().isForbidden());
    }

    @Test void ordinaryApplicantCannotCallApprovalServiceDirectly() {
        var applicant = signup(); var application = verified(applicant);
        assertThatThrownBy(() -> as(applicant, () -> applications.decide(application.getPublicId(), decision(application), true)))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test void simultaneousApproveAndRejectHaveOneWinner() throws Exception {
        var applicant = signup(); var administrator = admin(); var application = verified(applicant);
        long organizations = jdbc.queryForObject("SELECT count(*) FROM organization", Long.class);
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        try {
            var first = pool.submit(() -> { gate.await(); try { as(administrator, () -> applications.decide(application.getPublicId(), decision(application), true)); return true; } catch (ConflictException e) { return false; } });
            var second = pool.submit(() -> { gate.await(); try { as(administrator, () -> applications.decide(application.getPublicId(), decision(application), false)); return true; } catch (ConflictException e) { return false; } });
            gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            boolean approved = application(applicant).getStatus() == OrganizationApplication.Status.APPROVED;
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization", Long.class)).isEqualTo(organizations+(approved?1:0));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?", Integer.class,userId(applicant))).isEqualTo(approved?1:0);
        } finally { pool.shutdownNow(); }
    }

    @Test void outboxFailureRollsBackApprovalOrganizationMembershipAndAudit() {
        var applicant = signup(); var administrator = admin(); var application = verified(applicant);
        long before = jdbc.queryForObject("SELECT count(*) FROM organization", Long.class);
        doThrow(new IllegalStateException("test enqueue failure")).when((MailOutbox)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox)).enqueue(isNull(), any(), any());
        assertThatThrownBy(() -> as(administrator, () -> applications.decide(application.getPublicId(),decision(application),true))).isInstanceOf(IllegalStateException.class);
        assertThat(application(applicant).getStatus()).isEqualTo(OrganizationApplication.Status.PENDING_REVIEW);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization", Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?", Integer.class,userId(applicant))).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_audit_event WHERE target_id=? AND action='APPLICATION_APPROVED'", Integer.class,application.getPublicId().toString())).isZero();
    }

    @Test void resendRevokesPreviousTokenAndCancelsItsOutbox() {
        var applicant = signup(); String first = token(applicant);
        assertThatThrownBy(() -> as(applicant, () -> { verification.request(); return null; })).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        jdbc.update("UPDATE email_auth_token SET created_at=NOW()-INTERVAL '2 minutes' WHERE user_id=?", userId(applicant));
        as(applicant, () -> { verification.request(); return null; });
        String second = token(applicant);
        assertThat(second).isNotEqualTo(first);
        assertThatThrownBy(() -> verification.confirm(first)).isInstanceOf(BusinessException.class);
        verification.confirm(second);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox o JOIN email_auth_token t ON o.email_token_id=t.id WHERE t.user_id=? AND o.encrypted_payload IS NOT NULL", Integer.class,userId(applicant))).isZero();
    }

    @Test void expiredOrWrongEmailTokenCannotBeConsumed() {
        var applicant = signup(); String raw = token(applicant);
        jdbc.update("UPDATE email_auth_token SET expires_at=NOW()-INTERVAL '1 second' WHERE user_id=?",userId(applicant));
        assertThatThrownBy(() -> verification.confirm(raw)).isInstanceOf(BusinessException.class);
        var second = signup(); String secondRaw = token(second);
        jdbc.update("UPDATE email_auth_token SET target_email='wrong@example.com' WHERE user_id=?",userId(second));
        assertThatThrownBy(() -> verification.confirm(secondRaw)).isInstanceOf(BusinessException.class);
    }

    @Test void normalizedEmailCannotCreateDuplicateAccount() {
        var first = signup();
        assertThatThrownBy(() -> auth.signup(new SignupRequest("회사",null,"사용자", " "+first.response().user().email().toUpperCase(Locale.ROOT)+" ",PASSWORD,null))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO app_user(email,name) VALUES (?, 'duplicate')", first.response().user().email().toUpperCase(Locale.ROOT)));
    }

    @Test void duplicatePendingApplicationCannotBeSubmitted() {
        var applicant = signup();
        assertThatThrownBy(() -> as(applicant, () -> applications.submit(new ApplicationRequest("중복",null,null,null)))).isInstanceOf(ConflictException.class);
    }

    @Test void inactiveApplicantAndStaleVersionCannotBeApproved() {
        var applicant = signup(); var administrator = admin(); var application = verified(applicant);
        assertThatThrownBy(() -> as(administrator, () -> applications.decide(application.getPublicId(),new DecisionRequest(application.getVersion()+1,"확인",null),true))).isInstanceOf(ConflictException.class);
        jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE id=?",userId(applicant));
        assertThatThrownBy(() -> as(administrator, () -> applications.decide(application.getPublicId(),decision(application),true))).isInstanceOf(ConflictException.class);
    }

    @Test void verificationLinkGetDoesNotConsumeTokenAndApiDoesNotExposeToken() throws Exception {
        String email = UUID.randomUUID()+"@example.com";
        mvc.perform(post("/api/v1/auth/signup").contentType("application/json")
                .content(mapper.writeValueAsString(new SignupRequest("회사",null,"신청자",email,PASSWORD,null))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.nextPath").value("/application"))
                .andExpect(jsonPath("$.data.verificationToken").doesNotExist()).andExpect(jsonPath("$.data.user.role").isEmpty());
        mvc.perform(get("/api/v1/auth/email-verifications/confirm")).andExpect(status().isMethodNotAllowed());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_auth_token t JOIN app_user u ON t.user_id=u.id WHERE u.email=? AND t.consumed_at IS NOT NULL",Integer.class,email)).isZero();
    }

    @Test void duplicateApprovalsHaveExactlyOneOrganization() throws Exception {
        var applicant = signup(); var administrator = admin(); var application = verified(applicant);
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch gate = new CountDownLatch(1);
        try {
            Callable<Boolean> operation = () -> { gate.await(); try { as(administrator, () -> applications.decide(application.getPublicId(),decision(application),true)); return true; } catch(ConflictException e) { return false; } };
            var first=pool.submit(operation); var second=pool.submit(operation); gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member WHERE user_id=?",Integer.class,userId(applicant))).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test void simultaneousVerificationConsumesTokenOnce() throws Exception {
        var applicant=signup(); String raw=token(applicant);
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch gate=new CountDownLatch(1);
        try {
            Callable<Boolean> operation=() -> { gate.await(); try { verification.confirm(raw); return true; } catch(BusinessException e) { return false; } };
            var first=pool.submit(operation); var second=pool.submit(operation); gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        } finally { pool.shutdownNow(); }
    }

    @Test void signupRollsBackWhenVerificationMailCannotBeQueued() {
        String email=UUID.randomUUID()+"@example.com";
        long applicationsBefore=repository.count();
        doThrow(new IllegalStateException("test enqueue failure")).when((MailOutbox)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(outbox)).enqueue(any(),any(),any());
        assertThatThrownBy(() -> auth.signup(new SignupRequest("회사",null,"신청자",email,PASSWORD,null))).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmailIgnoreCase(email)).isEmpty();
        assertThat(repository.count()).isEqualTo(applicationsBefore);
    }

    @Test void failedVerificationAttemptsAreRateLimitedWithoutRevealingEmail() throws Exception {
        String address="192.0.2.123";
        for(int i=0;i<30;i++) {
            mvc.perform(post("/api/v1/auth/email-verifications/confirm").with(request -> { request.setRemoteAddr(address); return request; })
                    .contentType("application/json").content("{\"token\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/auth/email-verifications/confirm").with(request -> { request.setRemoteAddr(address); return request; })
                .contentType("application/json").content("{\"token\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"))
                .andExpect(status().isTooManyRequests());
    }
}

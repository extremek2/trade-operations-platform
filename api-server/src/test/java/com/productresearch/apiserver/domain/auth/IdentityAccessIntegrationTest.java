package com.productresearch.apiserver.domain.auth;

import com.productresearch.apiserver.PostgresTestSupport;
import com.productresearch.apiserver.domain.auth.dto.*;
import com.productresearch.apiserver.domain.auth.entity.RefreshSession;
import com.productresearch.apiserver.domain.auth.service.*;
import com.productresearch.apiserver.domain.identity.entity.*;
import com.productresearch.apiserver.domain.identity.repository.*;
import com.productresearch.apiserver.domain.platform.service.AdminBootstrapService;
import com.productresearch.apiserver.domain.platform.repository.*;
import com.productresearch.apiserver.domain.platform.entity.PlatformRoleAssignment;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityAccessIntegrationTest extends PostgresTestSupport {
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired PlatformRoleRepository roles;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository memberships;
    @Autowired IdentityAuditRepository audit;
    @Autowired PasswordEncoder passwords;
    @Autowired AdminBootstrapService bootstrap;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    static final String PASSWORD = "test-password-1234";

    AuthService.Tokens owner() {
        String email = UUID.randomUUID() + "@example.com";
        var user = users.save(AppUser.registered(email, passwords.encode(PASSWORD), "기존 화주", null));
        var organization = organizations.save(new Organization("기존 화주", Organization.Type.SHIPPER, null, email, null));
        memberships.save(new OrganizationMember(organization, user, OrganizationMember.Role.OWNER));
        return auth.login(new LoginRequest(email, PASSWORD, null));
    }
    AppUser account() {
        return users.save(AppUser.registered(UUID.randomUUID() + "@example.com", passwords.encode(PASSWORD), "사용자", null));
    }
    AuthService.Tokens platform() {
        AppUser user = account();
        roles.save(new PlatformRoleAssignment(user));
        return auth.platformLogin(new LoginRequest(user.getEmail(), PASSWORD, null));
    }

    @Test void organizationLoginAndRefreshRemainCompatible() throws Exception {
        var signed = owner();
        var logged = auth.login(new LoginRequest(signed.response().user().email(), PASSWORD, null));
        assertThat(logged.response().user().role()).isEqualTo(OrganizationMember.Role.OWNER);
        assertThat(logged.response().user().sessionKind()).isEqualTo(RefreshSession.Kind.ORGANIZATION);
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + logged.response().accessToken())).andExpect(status().isOk());
        var refreshed = auth.refresh(logged.refreshToken());
        assertThat(refreshed.refreshToken()).isNotEqualTo(logged.refreshToken());
        assertThatThrownBy(() -> auth.refresh(logged.refreshToken()));
        auth.logout(refreshed.refreshToken());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + refreshed.response().accessToken())).andExpect(status().isUnauthorized());
    }

    @Test void platformAccountNeedsNoOrganizationAndCannotReadShipments() throws Exception {
        var tokens = platform();
        assertThat(tokens.response().user().organizationId()).isNull();
        assertThat(auth.refresh(tokens.refreshToken()).response().user().systemAdmin()).isTrue();
        mvc.perform(get("/api/v1/system-admin/session").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isForbidden());
    }

    @Test void accountWithoutMembershipOnlyGetsAccountSession() throws Exception {
        var user = account();
        var tokens = auth.login(new LoginRequest(user.getEmail(), PASSWORD, null));
        assertThat(tokens.response().user().sessionKind()).isEqualTo(RefreshSession.Kind.ACCOUNT);
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/system-admin/session").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isForbidden());
    }

    @Test void publicSignupCannotGrantSystemRoleOrSwitchToPlatform() throws Exception {
        var owner = auth.signup(new SignupRequest("신청 화주", null, "담당자", UUID.randomUUID()+"@example.com", PASSWORD, null));
        mvc.perform(post("/api/v1/auth/context").header("Authorization", "Bearer " + owner.response().accessToken())
                .contentType("application/json").content("{\"kind\":\"PLATFORM\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/system-admin/session").header("Authorization", "Bearer " + owner.response().accessToken())).andExpect(status().isForbidden());
        assertThat(roles.existsByUserIdAndRoleAndActiveTrue(users.findByPublicId(owner.response().user().userId()).orElseThrow().getId(), PlatformRoleAssignment.Role.SYSTEM_ADMIN)).isFalse();
    }

    @Test void roleDowngradeAppliesToExistingAccessToken() throws Exception {
        var owner = owner();
        jdbc.update("UPDATE organization_member SET member_role='VIEWER' WHERE user_id=(SELECT id FROM app_user WHERE public_id=?)", owner.response().user().userId());
        mvc.perform(post("/api/v1/shipments").header("Authorization", "Bearer " + owner.response().accessToken())
                .contentType("application/json").content("{\"caseNumber\":\"TEST\",\"direction\":\"IMPORT\",\"transportMode\":\"AIR\"}"))
                .andExpect(status().isForbidden());
        assertThat(auth.refresh(owner.refreshToken()).response().user().role()).isEqualTo(OrganizationMember.Role.VIEWER);
    }

    @Test void revokedPlatformRoleBlocksAccessAndRefreshImmediately() throws Exception {
        var tokens = platform();
        jdbc.update("UPDATE platform_role_assignment SET active=false, revoked_at=NOW() WHERE user_id=(SELECT id FROM app_user WHERE public_id=?)", tokens.response().user().userId());
        mvc.perform(get("/api/v1/system-admin/session").header("Authorization", "Bearer " + tokens.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(tokens.refreshToken()));
    }

    @Test void inactiveUserAndOrganizationCannotRefreshOrUseAccessToken() throws Exception {
        var first = owner();
        jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE public_id=?", first.response().user().userId());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + first.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(first.refreshToken()));
        var second = owner();
        jdbc.update("UPDATE organization SET status='INACTIVE' WHERE public_id=?", second.response().user().organizationId());
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + second.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(second.refreshToken()));
    }

    @Test void concurrentRefreshHasExactlyOneWinner() throws Exception {
        var tokens = owner();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> refresh = () -> { start.await(); try { auth.refresh(tokens.refreshToken()); return true; } catch (RuntimeException e) { return false; } };
            var first = pool.submit(refresh); var second = pool.submit(refresh);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        } finally { pool.shutdownNow(); }
    }

    @Test void contextSwitchRevokesOldSessionAndCannotSelectAnotherOrganization() throws Exception {
        var owner = owner(); var other = owner();
        mvc.perform(post("/api/v1/auth/context").header("Authorization", "Bearer " + owner.response().accessToken())
                .contentType("application/json").content("{\"kind\":\"ORGANIZATION\",\"organizationId\":\"" + other.response().user().organizationId() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/context").header("Authorization", "Bearer " + owner.response().accessToken())
                .contentType("application/json").content("{\"kind\":\"ACCOUNT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user.sessionKind").value("ACCOUNT"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + owner.response().accessToken())).andExpect(status().isUnauthorized());
    }

    @Test void baselineCreatesSessionsAndRejectsInvalidContext() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version IN ('1','2') AND success", Integer.class)).isEqualTo(2);
        var tokens = owner();
        assertThatThrownBy(() -> jdbc.update("UPDATE refresh_session SET session_kind='PLATFORM' WHERE user_id=(SELECT id FROM app_user WHERE public_id=?)", tokens.response().user().userId()));
    }
    @Test void legacyAccessTokenWithoutSessionIdRequiresLoginAgain() throws Exception {
        var owner = owner();
        var now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder()
                .issuer("trade-ops-api").subject(owner.response().user().userId().toString())
                .issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("organizationId", owner.response().user().organizationId().toString())
                .claim("role", "OWNER").build();
        String legacy = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + legacy)).andExpect(status().isUnauthorized());
    }

    @Test void anonymousCannotReachPlatformAndExpiredSessionCannotAuthenticate() throws Exception {
        mvc.perform(get("/api/v1/system-admin/session")).andExpect(status().isUnauthorized());
        var owner = owner();
        jdbc.update("UPDATE refresh_session SET expires_at=NOW()-INTERVAL '1 second' WHERE user_id=(SELECT id FROM app_user WHERE public_id=?)", owner.response().user().userId());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + owner.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(owner.refreshToken()));
    }

    @Test void inactiveMembershipBlocksExistingSession() throws Exception {
        var owner = owner();
        jdbc.update("UPDATE organization_member SET status='INACTIVE' WHERE user_id=(SELECT id FROM app_user WHERE public_id=?)", owner.response().user().userId());
        mvc.perform(get("/api/v1/shipments").header("Authorization", "Bearer " + owner.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(owner.refreshToken()));
    }

}

package com.tradeoperationsplatform.apiserver.domain.identity;

import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import com.tradeoperationsplatform.apiserver.domain.auth.dto.LoginRequest;
import com.tradeoperationsplatform.apiserver.domain.auth.service.AuthService;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.PlatformRoleAssignment;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.PlatformRoleRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember.Role.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrganizationMemberIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired PlatformRoleRepository platformRoles;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository audit;
    static final String URL = "/api/v1/organizations/current/members";
    static final String PASSWORD = "test-password-1234";
    Organization organization;
    AppUser owner;
    String token;

    @BeforeEach void setup() {
        organization = organizations.save(new Organization("권한 테스트 화주", Organization.Type.SHIPPER, null, null, null));
        owner = member(organization, OWNER);
        token = login(owner).response().accessToken();
    }
    AppUser account(boolean verified) {
        var user = AppUser.registered(UUID.randomUUID()+"@example.test", passwords.encode(PASSWORD), "직원", null);
        if (verified) user.verifyEmail();
        return users.save(user);
    }
    AppUser member(Organization org, OrganizationMember.Role role) {
        var user = account(true); members.save(new OrganizationMember(org, user, role)); return user;
    }
    AuthService.Tokens login(AppUser user) { return auth.login(new LoginRequest(user.getEmail(), PASSWORD, null)); }
    ResultActions add(String actorToken, AppUser user, String role) throws Exception {
        return mvc.perform(post(URL).header("Authorization", "Bearer " + actorToken).contentType("application/json")
                .content(json.writeValueAsString(Map.of("email", user.getEmail().toUpperCase(Locale.ROOT), "role", role, "reason", "직원 등록"))));
    }
    ResultActions change(String actorToken, AppUser user, String role, String status, long version) throws Exception {
        return mvc.perform(patch(URL+"/"+user.getPublicId()).header("Authorization", "Bearer "+actorToken).contentType("application/json")
                .content(json.writeValueAsString(Map.of("role",role,"status",status,"version",version,"reason","조직 업무 변경"))));
    }

    @Test void ownerAddsPromotesAndDemotesEmployeeWithAudit() throws Exception {
        var user = account(true);
        add(token,user,"OPERATOR").andExpect(status().isCreated()).andExpect(jsonPath("$.data.version").value(0));
        change(token,user,"ADMIN","ACTIVE",0).andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1));
        change(token,user,"VIEWER","ACTIVE",1).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from identity_audit_event where organization_id=? and target_type='ORGANIZATION_MEMBER'", Integer.class, organization.getId())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select old_value from identity_audit_event where organization_id=? order by id desc limit 1", String.class, organization.getId())).isEqualTo("ADMIN:ACTIVE");
        assertThat(login(user).response().user().role()).isEqualTo(VIEWER);
    }

    @Test void adminManagesOnlyOperatorsAndViewers() throws Exception {
        var admin = member(organization,ADMIN); String adminToken=login(admin).response().accessToken();
        var employee=account(true);
        add(adminToken,employee,"VIEWER").andExpect(status().isCreated());
        change(adminToken,employee,"OPERATOR","ACTIVE",0).andExpect(status().isOk());
        change(adminToken,employee,"ADMIN","ACTIVE",1).andExpect(status().isForbidden());
        add(adminToken,account(true),"ADMIN").andExpect(status().isForbidden());
        var peer=member(organization,ADMIN);
        change(adminToken,peer,"VIEWER","INACTIVE",0).andExpect(status().isForbidden());
        change(adminToken,employee,"OPERATOR","INACTIVE",1).andExpect(status().isOk());
    }

    @Test void ownerAndSelfChangesCannotRemoveLastOwnerOrEscalate() throws Exception {
        change(token,owner,"VIEWER","INACTIVE",0).andExpect(status().isForbidden());
        var admin=member(organization,ADMIN); var adminToken=login(admin).response().accessToken();
        change(adminToken,admin,"OWNER","ACTIVE",0).andExpect(status().isForbidden());
        change(token,admin,"OWNER","ACTIVE",0).andExpect(status().isForbidden());
        add(token,account(true),"OWNER").andExpect(status().isForbidden());
        assertThat(members.findByOrganizationIdAndUserPublicId(organization.getId(),owner.getPublicId()).orElseThrow().getMemberRole()).isEqualTo(OWNER);
    }

    @Test void otherOrganizationTargetsAreNotVisibleOrMutable() throws Exception {
        var other=organizations.save(new Organization("다른 조직",Organization.Type.SHIPPER,null,null,null));
        var outsider=member(other,OPERATOR);
        mvc.perform(get(URL).header("Authorization","Bearer "+token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        change(token,outsider,"ADMIN","ACTIVE",0).andExpect(status().isNotFound());
        add(token,outsider,"OPERATOR").andExpect(status().isConflict());
    }

    @Test void nonManagersAndPlatformCannotListOrAddEmployees() throws Exception {
        for (var role:List.of(OPERATOR,VIEWER)) {
            var user=member(organization,role); var t=login(user).response().accessToken();
            mvc.perform(get(URL).header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
            add(t,account(true),"VIEWER").andExpect(status().isForbidden());
            change(t,owner,"VIEWER","INACTIVE",0).andExpect(status().isForbidden());
        }
        var platform=account(true); platformRoles.save(new PlatformRoleAssignment(platform));
        var t=auth.platformLogin(new LoginRequest(platform.getEmail(),PASSWORD,null)).response().accessToken();
        mvc.perform(get(URL).header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test void rejectsUnverifiedInactiveDuplicateAndMalformedRequests() throws Exception {
        add(token,account(false),"VIEWER").andExpect(status().isBadRequest());
        var inactive=account(true);
        jdbc.update("update app_user set status='INACTIVE' where id=?",inactive.getId());
        add(token,inactive,"VIEWER").andExpect(status().isBadRequest());
        var employee=member(organization,OPERATOR);
        add(token,employee,"VIEWER").andExpect(status().isConflict());
        mvc.perform(patch(URL+"/"+employee.getPublicId()).header("Authorization","Bearer "+token).contentType("application/json")
                .content("{\"role\":\"ADMIN\",\"status\":\"ACTIVE\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test void demotionImmediatelyBlocksOldAdminToken() throws Exception {
        var admin=member(organization,ADMIN); var signed=login(admin);
        change(token,admin,"VIEWER","ACTIVE",0).andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization","Bearer "+signed.response().accessToken())).andExpect(status().isForbidden());
        assertThat(auth.refresh(signed.refreshToken()).response().user().role()).isEqualTo(VIEWER);
    }

    @Test void deactivationRevokesSessionsPermanentlyEvenAfterReactivation() throws Exception {
        var employee=member(organization,OPERATOR); var signed=login(employee);
        change(token,employee,"OPERATOR","INACTIVE",0).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments").header("Authorization","Bearer "+signed.response().accessToken())).andExpect(status().isUnauthorized());
        change(token,employee,"OPERATOR","ACTIVE",1).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments").header("Authorization","Bearer "+signed.response().accessToken())).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(signed.refreshToken()));
        assertThat(login(employee).response().user().role()).isEqualTo(OPERATOR);
    }

    @Test void staleVersionDoesNotOverwriteNewerRole() throws Exception {
        var employee=member(organization,OPERATOR);
        change(token,employee,"ADMIN","ACTIVE",0).andExpect(status().isOk());
        change(token,employee,"VIEWER","ACTIVE",0).andExpect(status().isConflict());
        assertThat(members.findByOrganizationIdAndUserPublicId(organization.getId(),employee.getPublicId()).orElseThrow().getMemberRole()).isEqualTo(ADMIN);
    }

    @Test void concurrentRoleChangesHaveOneWinner() throws Exception {
        var employee=member(organization,OPERATOR);
        var results=concurrent(() -> change(token,employee,"ADMIN","ACTIVE",0).andReturn().getResponse().getStatus(),
                () -> change(token,employee,"VIEWER","ACTIVE",0).andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(200,409);
    }

    @Test void auditFailureRollsBackMembershipAndSessionRevocation() throws Exception {
        var employee=member(organization,OPERATOR); var signed=login(employee);
        org.mockito.Mockito.doThrow(new IllegalStateException("test audit failure")).when(audit).save(org.mockito.ArgumentMatchers.any());
        change(token,employee,"VIEWER","INACTIVE",0).andExpect(status().isInternalServerError());
        var member=members.findByOrganizationIdAndUserPublicId(organization.getId(),employee.getPublicId()).orElseThrow();
        assertThat(member.getStatus()).isEqualTo(OrganizationMember.Status.ACTIVE);
        assertThat(member.getMemberRole()).isEqualTo(OPERATOR);
        assertThat(member.getVersion()).isZero();
        mvc.perform(get("/api/v1/shipments").header("Authorization","Bearer "+signed.response().accessToken())).andExpect(status().isOk());
    }

    @Test void reactivationCannotCreateSecondActiveOrganization() throws Exception {
        var employee=member(organization,OPERATOR);
        change(token,employee,"OPERATOR","INACTIVE",0).andExpect(status().isOk());
        var other=organizations.save(new Organization("새 소속",Organization.Type.SHIPPER,null,null,null));
        members.save(new OrganizationMember(other,employee,OPERATOR));
        change(token,employee,"OPERATOR","ACTIVE",1).andExpect(status().isConflict());
    }

    @Test void concurrentDuplicateAddsHaveOneWinner() throws Exception {
        var employee=account(true);
        var results=concurrent(() -> add(token,employee,"OPERATOR").andReturn().getResponse().getStatus(),
                () -> add(token,employee,"VIEWER").andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(201,409);
    }

    List<Integer> concurrent(Callable<Integer> a,Callable<Integer> b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            var first=pool.submit(() -> {start.await();return a.call();});
            var second=pool.submit(() -> {start.await();return b.call();}); start.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
    }
}

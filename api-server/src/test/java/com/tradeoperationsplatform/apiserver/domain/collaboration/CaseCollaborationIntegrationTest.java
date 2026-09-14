package com.tradeoperationsplatform.apiserver.domain.collaboration;

import com.tradeoperationsplatform.apiserver.PostgresTestSupport;
import com.tradeoperationsplatform.apiserver.domain.auth.dto.LoginRequest;
import com.tradeoperationsplatform.apiserver.domain.auth.service.AuthService;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.util.*;
import org.springframework.test.util.AopTestUtils;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CaseCollaborationIntegrationTest extends PostgresTestSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired OrganizationRepository organizations;
    @Autowired OrganizationMemberRepository members;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired OutboxCipher cipher;
    @Autowired CollaborationService collaboration;
    @MockitoSpyBean MailOutbox outbox;
    Organization org; AppUser owner; String ownerToken; UUID shipment; String partnerId;
    static final String PASSWORD="test-password-1234";
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM auth_rate_limit");
        org=organizations.save(new Organization("협업 화주",Organization.Type.SHIPPER,null,null,null));
        owner=users.save(AppUser.registered(UUID.randomUUID()+"@example.test",passwords.encode(PASSWORD),"화주",null));
        members.save(new OrganizationMember(org,owner,OrganizationMember.Role.OWNER));
        ownerToken=auth.login(new LoginRequest(owner.getEmail(),PASSWORD,null)).response().accessToken();
        shipment=jdbc.queryForObject("INSERT INTO shipment_case(owner_organization_id,case_number,direction,transport_mode,created_by) VALUES (?,?,'IMPORT','SEA',?) RETURNING public_id",UUID.class,org.getId(),UUID.randomUUID().toString(),owner.getId());
        var c=body(call(post("/api/v1/organizations/current/partners"),ownerToken,Map.of("name","포워더","roles",List.of("FORWARDER"))).andExpect(status().isCreated())).get("businessPartnerId").asText();
        partnerId=body(call(post(base()+"/partners"),ownerToken,Map.of("businessPartnerId",c,"role","FORWARDER")).andExpect(status().isCreated())).get("casePartnerId").asText();
    }
    String base() {return "/api/v1/shipments/"+shipment;}
    String email() {return UUID.randomUUID()+"@example.test";}
    ResultActions call(MockHttpServletRequestBuilder request,String bearer,Object data) throws Exception {
        if(bearer!=null) request.header("Authorization","Bearer "+bearer);
        if(data!=null) request.contentType("application/json").content(json.writeValueAsString(data));
        return mvc.perform(request);
    }
    JsonNode body(ResultActions result) throws Exception {return json.readTree(result.andReturn().getResponse().getContentAsString()).get("data");}
    JsonNode invite(String email,String level) throws Exception {
        return body(invitation(email,level).andExpect(status().isCreated()));
    }
    ResultActions invitation(String email,String level) throws Exception {
        return call(post(base()+"/partners/"+partnerId+"/invitations"),ownerToken,Map.of("name","외부 담당자","email",email,"accessLevel",level));
    }
    String linkToken(String email) throws Exception {
        var payload=jdbc.queryForObject("SELECT o.encrypted_payload FROM mail_outbox o JOIN email_auth_token t ON t.id=o.email_token_id WHERE t.target_email=? AND t.purpose='CASE_LOGIN' ORDER BY t.id DESC LIMIT 1",String.class,email);
        return json.readValue(cipher.decrypt(payload),MailMessage.class).body().split("&token=")[1];
    }
    JsonNode accept(String email) throws Exception {
        return body(call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",linkToken(email))).andExpect(status().isOk()));
    }
    String participant(String email) {return jdbc.queryForObject("SELECT p.public_id FROM case_participant p JOIN external_contact ec ON ec.id=p.external_contact_id JOIN shipment_case s ON s.id=p.shipment_case_id WHERE s.public_id=? AND ec.email=?",UUID.class,shipment,email).toString();}
    ResultActions revoke(String email) throws Exception {return call(post(base()+"/participants/"+participant(email)+"/revoke"),ownerToken,Map.of("reason","담당 업무 종료"));}

    @Test void newExternalAccountHasOnlyCaseAccessAndCannotReplayToken() throws Exception {
        String email=email(); invite(email,"VIEWER"); String raw=linkToken(email); var session=accept(email);
        String token=session.get("accessToken").asText();
        assertThat(session.at("/user/sessionKind").asText()).isEqualTo("CASE");
        assertThat(session.at("/user/organizationId").isNull()).isTrue();
        assertThat(session.at("/user/shipmentId").asText()).isEqualTo(shipment.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM organization_member m JOIN app_user u ON u.id=m.user_id WHERE u.email=?",Integer.class,email)).isZero();
        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().getPasswordHash()).isNull();
        call(get("/api/v1/case-workspace/"+shipment),token,null).andExpect(status().isOk()).andExpect(jsonPath("$.data.shipperReference").doesNotExist());
        call(get("/api/v1/case-workspace/"+UUID.randomUUID()),token,null).andExpect(status().isNotFound());
        for(String path:List.of("/api/v1/shipments",base(),"/api/v1/organizations/current/members","/api/v1/system-admin/applications",base()+"/partners"))
            call(get(path),token,null).andExpect(status().isForbidden());
        call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andExpect(status().isBadRequest());
    }
    @Test void contributorMayAddOnlyTransportDocumentNumber() throws Exception {
        var email=email(); invite(email,"CONTRIBUTOR"); String token=accept(email).get("accessToken").asText();
        var doc=Map.of("documentType","HBL","documentNumber","HBL-TEST","primary",false);
        call(post("/api/v1/case-workspace/"+shipment+"/documents"),token,doc).andExpect(status().isCreated());
        call(post("/api/v1/case-workspace/"+shipment+"/documents"),token,doc).andExpect(status().isConflict());
        call(post("/api/v1/case-workspace/"+shipment+"/documents"),token,Map.of("documentType","MBL","documentNumber","PRIMARY","primary",true)).andExpect(status().isBadRequest());
        call(post(base()+"/archive"),token,null).andExpect(status().isForbidden());
        call(patch(base()),token,Map.of("version",0,"priority","URGENT")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM identity_audit_event WHERE organization_id=? AND action='CASE_DOCUMENT_ADDED'",Integer.class,org.getId())).isEqualTo(1);
    }
    @Test void viewerCannotAddDocuments() throws Exception {
        String email=email(); invite(email,"VIEWER"); var token=accept(email).get("accessToken").asText();
        call(post("/api/v1/case-workspace/"+shipment+"/documents"),token,Map.of("documentType","MBL","documentNumber","NO")).andExpect(status().isForbidden());
    }
    @Test void simultaneousInvitesCannotExceedThree() throws Exception {
        invite(email(),"VIEWER"); invite(email(),"VIEWER");
        var results=concurrent(() -> invitation(email(),"VIEWER").andReturn().getResponse().getStatus(),() -> invitation(email(),"VIEWER").andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(201,409);
        assertThat(body(call(get(base()+"/partners"),ownerToken,null)).get(0).get("occupied").asInt()).isEqualTo(3);
    }
    @Test void duplicateEmailInCaseIsRejectedAndRevocationFreesSlot() throws Exception {
        var email=email(); invite(email,"VIEWER"); invitation(email,"CONTRIBUTOR").andExpect(status().isConflict());
        invite(email(),"VIEWER"); invite(email(),"VIEWER"); invitation(email(),"VIEWER").andExpect(status().isConflict());
        revoke(email).andExpect(status().isOk()); invite(email(),"VIEWER");
    }
    @Test void revokeBlocksOldSessionRefreshAndLinksEvenAfterReinvite() throws Exception {
        String email=email(); invite(email,"CONTRIBUTOR"); String raw=linkToken(email);
        var accepted=call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andExpect(status().isOk());
        String token=body(accepted).get("accessToken").asText();
        String cookie=accepted.andReturn().getResponse().getHeader("Set-Cookie"); String refresh=cookie.split(";",2)[0].split("=",2)[1];
        revoke(email).andExpect(status().isOk()); invite(email,"VIEWER");
        call(get("/api/v1/case-workspace/"+shipment),token,null).andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> auth.refresh(refresh));
        call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andExpect(status().isBadRequest());
        assertThat(accept(email).at("/user/caseAccessLevel").asText()).isEqualTo("VIEWER");
    }
    @Test void concurrentTokenConsumptionHasOneWinner() throws Exception {
        var email=email(); invite(email,"VIEWER"); String raw=linkToken(email);
        var results=concurrent(() -> call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andReturn().getResponse().getStatus(),
                () -> call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(200,400);
    }
    @Test void expiredInvitationDoesNotConsumeCapacityAndCannotBeAccepted() throws Exception {
        var email=email(); var i=invite(email,"VIEWER"); String raw=linkToken(email);
        jdbc.update("UPDATE case_invitation SET expires_at=NOW()-INTERVAL '1 day' WHERE public_id=?",UUID.fromString(i.get("invitationId").asText()));
        call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",raw)).andExpect(status().isBadRequest());
        invite(email(),"VIEWER"); invite(email(),"VIEWER"); invite(email(),"VIEWER");
        assertThat(body(call(get(base()+"/partners"),ownerToken,null)).get(0).get("occupied").asInt()).isEqualTo(3);
    }
    @Test void reissueInvalidatesOldLinkAndWorksAfterAcceptedInvitationDeadline() throws Exception {
        var email=email(); var i=invite(email,"VIEWER"); String old=linkToken(email); accept(email);
        jdbc.update("UPDATE case_invitation SET expires_at=NOW()-INTERVAL '1 day' WHERE public_id=?",UUID.fromString(i.get("invitationId").asText()));
        jdbc.update("UPDATE email_auth_token SET created_at=NOW()-INTERVAL '2 minutes' WHERE target_email=?",email);
        call(post("/api/v1/auth/case-links/request"),null,Map.of("invitationId",i.get("invitationId").asText(),"email",email)).andExpect(status().isOk());
        assertThat(linkToken(email)).isNotEqualTo(old);
        assertThat(accept(email).at("/user/sessionKind").asText()).isEqualTo("CASE");
    }
    @Test void unknownEmailDoesNotSendAndRateLimitIsEnforced() throws Exception {
        var email=email(); var i=invite(email,"VIEWER");
        int count=jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE target_email=?",Integer.class,email);
        call(post("/api/v1/auth/case-links/request"),null,Map.of("invitationId",i.get("invitationId").asText(),"email",email())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE target_email=?",Integer.class,email)).isEqualTo(count);
        call(post("/api/v1/auth/case-links/request"),null,Map.of("invitationId",i.get("invitationId").asText(),"email",email)).andExpect(status().isTooManyRequests());
    }
    @Test void outboxFailureRollsBackInvitationAndContact() throws Exception {
        String email=email(); doThrow(new IllegalStateException("test mail failure")).when((MailOutbox)AopTestUtils.getUltimateTargetObject(outbox)).enqueue(any(),any(),any());
        invitation(email,"VIEWER").andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM external_contact WHERE email=?",Integer.class,email)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM case_invitation WHERE target_email=?",Integer.class,email)).isZero();
    }
    @Test void organizationAndUserDeactivationOrArchiveBlocksCaseSession() throws Exception {
        var email=email(); invite(email,"VIEWER"); String token=accept(email).get("accessToken").asText();
        jdbc.update("UPDATE shipment_case SET archived_at=NOW() WHERE public_id=?",shipment);
        call(get("/api/v1/case-workspace/"+shipment),token,null).andExpect(status().isUnauthorized());
        invitation(email(),"VIEWER").andExpect(status().isConflict());
        jdbc.update("UPDATE shipment_case SET archived_at=NULL WHERE public_id=?",shipment);
        jdbc.update("UPDATE app_user SET status='INACTIVE' WHERE email=?",email);
        call(get("/api/v1/case-workspace/"+shipment),token,null).andExpect(status().isUnauthorized());
    }
    @Test void existingAccountKeepsPasswordAndMembershipButCaseSessionIsRestricted() throws Exception {
        String email=owner.getEmail(); invite(email,"VIEWER"); String original=users.findById(owner.getId()).orElseThrow().getPasswordHash();
        String caseToken=accept(email).get("accessToken").asText();
        assertThat(users.findById(owner.getId()).orElseThrow().getPasswordHash()).isEqualTo(original);
        call(get("/api/v1/shipments"),caseToken,null).andExpect(status().isForbidden());
        call(get("/api/v1/shipments"),ownerToken,null).andExpect(status().isOk());
    }
    @Test void managerCannotAttachOtherOrganizationsPartnerAndOperatorCannotInvite() throws Exception {
        var other=organizations.save(new Organization("다른 화주",Organization.Type.SHIPPER,null,null,null));
        var c=jdbc.queryForObject("INSERT INTO business_partner(owner_organization_id,name) VALUES (?,'다른 포워더') RETURNING public_id",UUID.class,other.getId());
        jdbc.update("INSERT INTO business_partner_role(business_partner_id,partner_role) SELECT id,'FORWARDER' FROM business_partner WHERE public_id=?",c);
        call(post(base()+"/partners"),ownerToken,Map.of("businessPartnerId",c,"role","FORWARDER")).andExpect(status().isNotFound());
        jdbc.update("UPDATE organization_member SET member_role='OPERATOR' WHERE user_id=?",owner.getId());
        invitation(email(),"VIEWER").andExpect(status().isForbidden());
        call(get(base()+"/partners"),ownerToken,null).andExpect(status().isForbidden());
    }
    @Test void businessPartnerKeepsMultipleRolesAndAttachmentUsesAnExplicitRole() throws Exception {
        JsonNode created=body(call(post("/api/v1/organizations/current/partners"),ownerToken,
                Map.of("name","복합 거래처 "+UUID.randomUUID(),"roles",List.of("SUPPLIER","FORWARDER")))
                .andExpect(status().isCreated()));
        JsonNode found=null;
        for(JsonNode candidate:body(call(get("/api/v1/organizations/current/partners"),ownerToken,null))) {
            if(candidate.get("businessPartnerId").asText().equals(created.get("businessPartnerId").asText())) found=candidate;
        }
        assertThat(found).isNotNull();
        assertThat(json.treeToValue(found.get("roles"),String[].class)).containsExactlyInAnyOrder("SUPPLIER","FORWARDER");
        call(post(base()+"/partners"),ownerToken,Map.of(
                "businessPartnerId",created.get("businessPartnerId").asText(),"role","SUPPLIER"))
                .andExpect(status().isBadRequest());
        call(post(base()+"/partners"),ownerToken,Map.of(
                "businessPartnerId",created.get("businessPartnerId").asText(),"role","FORWARDER"))
                .andExpect(status().isCreated());
    }
    @Test void verificationPurposeCannotConsumeCaseLink() throws Exception {
        var email=email(); invite(email,"VIEWER"); String raw=linkToken(email);
        call(post("/api/v1/auth/email-verifications/confirm"),null,Map.of("token",raw)).andExpect(status().isBadRequest());
        accept(email);
    }

    @Test void unconsumedLinkIsRevokedOnReissueAndExpiredLoginTokenCannotAuthenticate() throws Exception {
        var email=email(); var i=invite(email,"VIEWER"); String old=linkToken(email);
        jdbc.update("UPDATE email_auth_token SET created_at=NOW()-INTERVAL '2 minutes' WHERE target_email=?",email);
        call(post("/api/v1/auth/case-links/request"),null,Map.of("invitationId",i.get("invitationId").asText(),"email",email)).andExpect(status().isOk());
        call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",old)).andExpect(status().isBadRequest());
        String fresh=linkToken(email);
        jdbc.update("UPDATE email_auth_token SET expires_at=NOW()-INTERVAL '1 second' WHERE target_email=?",email);
        call(post("/api/v1/auth/case-links/confirm"),null,Map.of("token",fresh)).andExpect(status().isBadRequest());
    }

    @Test void sameEmailCanJoinTwoCasesAndRevocationIsScopedToOneCase() throws Exception {
        var email=email(); invite(email,"VIEWER"); var first=accept(email); UUID firstShipment=shipment;
        var businessPartnerId=body(call(get(base()+"/partners"),ownerToken,null)).get(0).get("businessPartnerId").asText();
        UUID second=jdbc.queryForObject("INSERT INTO shipment_case(owner_organization_id,case_number,direction,transport_mode,created_by) VALUES (?,?,'IMPORT','SEA',?) RETURNING public_id",UUID.class,org.getId(),UUID.randomUUID().toString(),owner.getId());
        String secondPartner=body(call(post("/api/v1/shipments/"+second+"/partners"),ownerToken,Map.of("businessPartnerId",businessPartnerId,"role","FORWARDER")).andExpect(status().isCreated())).get("casePartnerId").asText();
        call(post("/api/v1/shipments/"+second+"/partners/"+secondPartner+"/invitations"),ownerToken,Map.of("name","같은 담당자","email",email,"accessLevel","CONTRIBUTOR")).andExpect(status().isCreated());
        var secondSession=accept(email);
        assertThat(first.at("/user/userId").asText()).isEqualTo(secondSession.at("/user/userId").asText());
        revoke(email).andExpect(status().isOk());
        call(get("/api/v1/case-workspace/"+firstShipment),first.get("accessToken").asText(),null).andExpect(status().isUnauthorized());
        call(get("/api/v1/case-workspace/"+second),secondSession.get("accessToken").asText(),null).andExpect(status().isOk());
        call(get("/api/v1/case-workspace/"+firstShipment),secondSession.get("accessToken").asText(),null).andExpect(status().isNotFound());
    }
    List<Integer> concurrent(Callable<Integer> a,Callable<Integer> b) throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {var first=pool.submit(() -> {start.await();return a.call();});var second=pool.submit(() -> {start.await();return b.call();});start.countDown();return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));}
        finally {pool.shutdownNow();}
    }
}

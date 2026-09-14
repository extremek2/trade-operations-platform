package com.tradeoperationsplatform.apiserver.domain.collaboration;

import com.tradeoperationsplatform.apiserver.domain.auth.service.*;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import static com.tradeoperationsplatform.apiserver.domain.collaboration.CollaborationRequests.*;

@Service
@RequiredArgsConstructor
public class CollaborationService {
    private final CurrentActor actor;
    private final JdbcTemplate jdbc;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final EntityManager entityManager;
    private final TokenService tokens;
    private final MailOutbox outbox;
    private final AuthService auth;
    private final com.tradeoperationsplatform.apiserver.domain.onboarding.service.EmailVerificationService verification;
    @Value("${app.mail.case-access-url:http://localhost:3000/external-access}") private String accessUrl;
    @Value("${app.collaboration.invitation-days:7}") private int invitationDays;
    @Value("${app.collaboration.login-minutes:15}") private int loginMinutes;

    @Transactional
    public Map<String,Object> attach(UUID shipmentId,Attach request) {
        var a=manager(true); var s=ownedShipment(shipmentId,a.organization().getId(),true);
        if(!List.of(com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner.Role.FORWARDER,
                com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner.Role.CUSTOMS_BROKER).contains(request.role()))
            throw new BusinessException("건별 협업에는 포워더 또는 관세사 역할만 연결할 수 있습니다.");
        var businessPartners=jdbc.queryForList("SELECT p.id FROM business_partner p JOIN business_partner_role r ON r.business_partner_id=p.id WHERE p.public_id=? AND p.owner_organization_id=? AND p.status='ACTIVE' AND r.partner_role=?",request.businessPartnerId(),a.organization().getId(),request.role().name());
        if(businessPartners.isEmpty()) throw missing();
        var rows=jdbc.queryForList("INSERT INTO case_partner(shipment_case_id,business_partner_id,partner_role) VALUES (?,?,?) RETURNING public_id AS \"casePartnerId\"",s.get("id"),businessPartners.get(0).get("id"),request.role().name());
        audit(a.user().getId(),a.organization().getId(),"CASE_PARTNER_ATTACHED","CASE_PARTNER",rows.get(0).get("casePartnerId"),null,request.businessPartnerId()+":"+request.role(),"건별 업체 연결");
        return rows.get(0);
    }
    @Transactional(readOnly=true)
    public List<Map<String,Object>> partners(UUID shipmentId) {
        var a=manager(false); var s=ownedShipment(shipmentId,a.organization().getId(),false);
        var rows=jdbc.queryForList("""
            SELECT cp.public_id AS "casePartnerId",c.public_id AS "businessPartnerId",c.name,cp.partner_role AS role,cp.id
            FROM case_partner cp JOIN business_partner c ON c.id=cp.business_partner_id
            WHERE cp.shipment_case_id=? ORDER BY cp.id
            """,s.get("id"));
        for(var row:rows) {
            var participants=jdbc.queryForList("""
                SELECT p.public_id AS "participantId",ec.name,ec.email,p.access_level AS "accessLevel",
                  CASE WHEN i.invitation_status='EXPIRED' OR (p.status='INVITED' AND i.expires_at<=NOW()) THEN 'EXPIRED' ELSE p.status END AS status,
                  i.public_id AS "invitationId",i.expires_at AS "expiresAt"
                FROM case_participant p JOIN external_contact ec ON ec.id=p.external_contact_id
                JOIN LATERAL (SELECT * FROM case_invitation WHERE participant_id=p.id ORDER BY id DESC LIMIT 1) i ON true
                WHERE p.case_partner_id=? ORDER BY p.id
                """,row.remove("id"));
            row.put("participants",participants);
            row.put("occupied",participants.stream().filter(p -> List.of("INVITED","ACTIVE").contains(p.get("status"))).count());
        }
        return rows;
    }
    @Transactional
    public Map<String,Object> invite(UUID shipmentId,UUID partnerId,Invite request) {
        var a=manager(true); var s=ownedShipment(shipmentId,a.organization().getId(),true);
        var cp=partner(partnerId,s.get("id"));
        expire(cp.get("id"));
        if(jdbc.queryForObject("SELECT count(*) FROM case_participant WHERE case_partner_id=? AND status IN ('INVITED','ACTIVE')",Integer.class,cp.get("id"))>=3)
            throw new ConflictException("이 건의 업체별 담당자는 초대 대기와 참여 중을 합해 최대 3명입니다.");
        String email=request.email().trim().toLowerCase(Locale.ROOT);
        var contacts=jdbc.queryForList("SELECT * FROM external_contact WHERE owner_organization_id=? AND lower(btrim(email))=?",a.organization().getId(),email);
        Long contactId;
        if(contacts.isEmpty()) contactId=jdbc.queryForObject("INSERT INTO external_contact(owner_organization_id,business_partner_id,name,email) VALUES (?,?,?,?) RETURNING id",Long.class,a.organization().getId(),cp.get("business_partner_id"),request.name().trim(),email);
        else {
            if(contacts.size()!=1 || !Objects.equals(contacts.get(0).get("business_partner_id"),cp.get("business_partner_id")))
                throw new ConflictException("이 이메일은 다른 업체 또는 기존 미분류 연락처에 등록되어 있습니다. 연락처 소속을 확인해 주세요.");
            contactId=((Number)contacts.get(0).get("id")).longValue();
        }
        var old=jdbc.queryForList("SELECT id,status,case_partner_id FROM case_participant WHERE shipment_case_id=? AND external_contact_id=?",s.get("id"),contactId);
        Long participantId;
        if(!old.isEmpty()) {
            var p=old.get(0);
            if(!"REVOKED".equals(p.get("status")) || !Objects.equals(p.get("case_partner_id"),cp.get("id"))) throw new ConflictException("이미 이 건에 초대되었거나 참여 중인 담당자입니다.");
            participantId=((Number)p.get("id")).longValue();
            revokeTokens(participantId);
            jdbc.update("UPDATE case_participant SET user_id=NULL,status='INVITED',access_level=?,joined_at=NULL WHERE id=?",request.accessLevel().name(),participantId);
        } else participantId=jdbc.queryForObject("INSERT INTO case_participant(shipment_case_id,case_partner_id,external_contact_id,participant_role,access_level,status) VALUES (?,?,?,?,?,'INVITED') RETURNING id",Long.class,s.get("id"),cp.get("id"),contactId,cp.get("partner_role"),request.accessLevel().name());
        Long invitationId=jdbc.queryForObject("INSERT INTO case_invitation(participant_id,token_hash,target_email,expires_at,created_by) VALUES (?,?,?,?,?) RETURNING id",Long.class,participantId,tokens.hash(tokens.newRefreshToken()),email,LocalDateTime.now().plusDays(invitationDays),a.user().getId());
        sendLink(invitationId);
        audit(a.user().getId(),a.organization().getId(),"CASE_INVITED","CASE_PARTICIPANT",participantId,null,request.accessLevel().name(),"담당자 이메일 초대");
        return jdbc.queryForList("SELECT public_id AS \"invitationId\",expires_at AS \"expiresAt\" FROM case_invitation WHERE id=?",invitationId).get(0);
    }
    @Transactional
    public void revoke(UUID shipmentId,UUID participantId,String reason) {
        var a=manager(true); var s=ownedShipment(shipmentId,a.organization().getId(),false);
        var rows=jdbc.queryForList("SELECT p.* FROM case_participant p JOIN case_partner cp ON cp.id=p.case_partner_id WHERE p.public_id=? AND p.shipment_case_id=? FOR UPDATE OF cp",participantId,s.get("id"));
        if(rows.isEmpty()) throw missing();
        var p=rows.get(0);
        if("REVOKED".equals(p.get("status"))) throw new ConflictException("이미 철회된 담당자입니다.");
        revokeTokens(p.get("id"));
        jdbc.update("UPDATE case_participant SET status='REVOKED' WHERE id=?",p.get("id"));
        audit(a.user().getId(),a.organization().getId(),"CASE_REVOKED","CASE_PARTICIPANT",p.get("id"),p.get("status").toString(),"REVOKED",reason.trim());
    }
    @Transactional
    public void requestLink(RequestLink request) {
        var rows=jdbc.queryForList("SELECT i.id,p.shipment_case_id FROM case_invitation i JOIN case_participant p ON p.id=i.participant_id WHERE i.public_id=? AND i.target_email=?",request.invitationId(),request.email().trim().toLowerCase(Locale.ROOT));
        // Same response for unknown, expired, revoked and valid invitations.
        if(rows.isEmpty()) return;
        lockShipment(rows.get(0).get("shipment_case_id"));
        if(!eligible(rows.get(0).get("id"))) return;
        sendLink(((Number)rows.get(0).get("id")).longValue());
    }
    @Transactional
    public AuthService.Tokens confirm(String raw) {
        String hash=tokens.hash(raw);
        var rows=jdbc.queryForList("SELECT t.invitation_id,p.shipment_case_id FROM email_auth_token t JOIN case_invitation i ON i.id=t.invitation_id JOIN case_participant p ON p.id=i.participant_id WHERE t.token_hash=? AND t.purpose='CASE_LOGIN'",hash);
        if(rows.isEmpty()) throw invalid();
        var row=rows.get(0); var s=lockShipment(row.get("shipment_case_id"));
        if(!eligible(row.get("invitation_id"))) throw invalid();
        var invitation=jdbc.queryForList("SELECT * FROM case_invitation WHERE id=? FOR UPDATE",row.get("invitation_id")).get(0);
        var consumed=jdbc.queryForList("UPDATE email_auth_token SET consumed_at=NOW() WHERE token_hash=? AND invitation_id=? AND purpose='CASE_LOGIN' AND target_email=? AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at>NOW() RETURNING id",hash,invitation.get("id"),invitation.get("target_email"));
        if(consumed.isEmpty()) throw invalid();
        String email=invitation.get("target_email").toString();
        var p=jdbc.queryForList("SELECT p.*,ec.name FROM case_participant p JOIN external_contact ec ON ec.id=p.external_contact_id WHERE p.id=?",invitation.get("participant_id")).get(0);
        // No password or organization membership is created for an external participant.
        jdbc.update("INSERT INTO app_user(public_id,email,name,status,created_at,updated_at) VALUES (?,?,?,'ACTIVE',NOW(),NOW()) ON CONFLICT DO NOTHING",UUID.randomUUID(),email,p.get("name"));
        var user=users.findByEmailIgnoreCase(email).orElseThrow(CollaborationService::invalid);
        users.lockById(user.getId()).orElseThrow(); entityManager.refresh(user);
        if(user.getStatus()!=AppUser.Status.ACTIVE) throw invalid();
        if(p.get("user_id")!=null && !p.get("user_id").equals(user.getId())) throw invalid();
        verification.markVerified(user);
        jdbc.update("UPDATE case_participant SET user_id=?,status='ACTIVE',joined_at=COALESCE(joined_at,NOW()) WHERE id=?",user.getId(),p.get("id"));
        jdbc.update("UPDATE case_invitation SET accepted_at=COALESCE(accepted_at,NOW()),invitation_status='ACCEPTED' WHERE id=?",invitation.get("id"));
        jdbc.update("UPDATE mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE status='PENDING' AND email_token_id=?",consumed.get(0).get("id"));
        audit(user.getId(),((Number)s.get("owner_organization_id")).longValue(),"CASE_LINK_CONFIRMED","CASE_PARTICIPANT",p.get("id"),p.get("status").toString(),"ACTIVE","이메일 링크 확인");
        return auth.issueCase(user,((Number)p.get("id")).longValue());
    }
    private void sendLink(Long invitationId) {
        var i=jdbc.queryForList("SELECT * FROM case_invitation WHERE id=? FOR UPDATE",invitationId).get(0);
        long recent=jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE invitation_id=? AND created_at>NOW()-INTERVAL '1 minute'",Long.class,invitationId);
        long hourly=jdbc.queryForObject("SELECT count(*) FROM email_auth_token WHERE invitation_id=? AND created_at>NOW()-INTERVAL '1 hour'",Long.class,invitationId);
        if(recent>0 || hourly>=5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"잠시 후 다시 요청해 주세요.");
        URI base=URI.create(accessUrl);
        if(base.getHost()==null || base.getRawQuery()!=null || base.getRawFragment()!=null || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && "localhost".equals(base.getHost())))) throw new IllegalStateException("Invalid case access URL");
        jdbc.update("UPDATE email_auth_token SET revoked_at=NOW() WHERE invitation_id=? AND consumed_at IS NULL AND revoked_at IS NULL",invitationId);
        jdbc.update("UPDATE mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE status='PENDING' AND email_token_id IN (SELECT id FROM email_auth_token WHERE invitation_id=? AND revoked_at IS NOT NULL)",invitationId);
        String raw=tokens.newRefreshToken(); LocalDateTime expires=LocalDateTime.now().plusMinutes(loginMinutes);
        Long id=jdbc.queryForObject("INSERT INTO email_auth_token(purpose,invitation_id,target_email,token_hash,expires_at) VALUES ('CASE_LOGIN',?,?,?,?) RETURNING id",Long.class,invitationId,i.get("target_email"),tokens.hash(raw),expires);
        outbox.enqueue(id,new MailMessage(i.get("target_email").toString(),"건별 업무 참여 및 접속 안내",
            "초대된 건에서만 업무에 참여할 수 있습니다. 접속 링크는 "+loginMinutes+"분, 최초 초대 수락은 "+invitationDays+"일 동안 유효합니다. 접속 링크가 만료되면 같은 페이지에서 새 링크를 요청해 주세요.\n"+accessUrl+"#invitation="+i.get("public_id")+"&token="+raw),expires);
    }
    private boolean eligible(Object invitationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM case_invitation i JOIN case_participant p ON p.id=i.participant_id
            JOIN case_partner cp ON cp.id=p.case_partner_id JOIN business_partner c ON c.id=cp.business_partner_id
            JOIN shipment_case s ON s.id=p.shipment_case_id JOIN organization o ON o.id=s.owner_organization_id
            WHERE i.id=? AND i.revoked_at IS NULL AND i.invitation_status IN ('PENDING','ACCEPTED')
            AND (i.accepted_at IS NOT NULL OR i.expires_at>NOW()) AND p.status IN ('INVITED','ACTIVE')
            AND cp.shipment_case_id=s.id AND c.owner_organization_id=s.owner_organization_id
            AND s.archived_at IS NULL AND o.status='ACTIVE')
            """,Boolean.class,invitationId));
    }
    private void expire(Object casePartnerId) {
        var rows=jdbc.queryForList("SELECT i.id,i.participant_id FROM case_invitation i JOIN case_participant p ON p.id=i.participant_id WHERE p.case_partner_id=? AND p.status='INVITED' AND i.invitation_status='PENDING' AND i.expires_at<=NOW()",casePartnerId);
        for(var i:rows) {
            revokeTokens(i.get("participant_id"));
            jdbc.update("UPDATE case_invitation SET invitation_status='EXPIRED' WHERE id=?",i.get("id"));
            jdbc.update("UPDATE case_participant SET status='REVOKED' WHERE id=?",i.get("participant_id"));
        }
    }
    private void revokeTokens(Object participantId) {
        jdbc.update("UPDATE case_invitation SET revoked_at=COALESCE(revoked_at,NOW()),invitation_status=CASE WHEN invitation_status='EXPIRED' THEN 'EXPIRED' ELSE 'REVOKED' END WHERE participant_id=?",participantId);
        jdbc.update("UPDATE email_auth_token SET revoked_at=NOW() WHERE invitation_id IN (SELECT id FROM case_invitation WHERE participant_id=?) AND revoked_at IS NULL",participantId);
        jdbc.update("UPDATE mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE status='PENDING' AND email_token_id IN (SELECT t.id FROM email_auth_token t JOIN case_invitation i ON i.id=t.invitation_id WHERE i.participant_id=?)",participantId);
        jdbc.update("UPDATE refresh_session SET revoked_at=NOW() WHERE participant_id=? AND revoked_at IS NULL",participantId);
    }
    private CurrentActor.Context manager(boolean lock) {
        var a=actor.require();
        if(lock) {
            organizations.lockById(a.organization().getId()).orElseThrow();
            entityManager.refresh(a.organization()); entityManager.refresh(a.member()); entityManager.refresh(a.user());
        }
        if(a.user().getStatus()!=AppUser.Status.ACTIVE || a.organization().getStatus()!=Organization.Status.ACTIVE || a.member().getStatus()!=OrganizationMember.Status.ACTIVE || !List.of(OrganizationMember.Role.OWNER,OrganizationMember.Role.ADMIN).contains(a.member().getMemberRole())) throw new AccessDeniedException("외부 초대 관리 권한이 필요합니다.");
        return a;
    }
    private Map<String,Object> ownedShipment(UUID id,Long orgId,boolean editable) {
        var rows=jdbc.queryForList("SELECT * FROM shipment_case WHERE public_id=? AND owner_organization_id=?"+(editable ? " FOR UPDATE" : ""),id,orgId);
        if(rows.isEmpty()) throw missing();
        if(editable && rows.get(0).get("archived_at")!=null) throw new ConflictException("보관된 건에는 초대할 수 없습니다.");
        return rows.get(0);
    }
    private Map<String,Object> lockShipment(Object shipmentId) {
        var rows=jdbc.queryForList("SELECT owner_organization_id FROM shipment_case WHERE id=?",shipmentId);
        if(rows.isEmpty()) throw invalid();
        jdbc.queryForList("SELECT id FROM organization WHERE id=? FOR UPDATE",rows.get(0).get("owner_organization_id"));
        return jdbc.queryForList("SELECT * FROM shipment_case WHERE id=? FOR UPDATE",shipmentId).get(0);
    }
    private Map<String,Object> partner(UUID id,Object shipmentId) {
        var rows=jdbc.queryForList("SELECT cp.*,c.name FROM case_partner cp JOIN business_partner c ON c.id=cp.business_partner_id WHERE cp.public_id=? AND cp.shipment_case_id=? FOR UPDATE OF cp",id,shipmentId);
        if(rows.isEmpty()) throw missing(); return rows.get(0);
    }
    private void audit(Long userId,Long orgId,String action,String type,Object target,String before,String after,String reason) {
        jdbc.update("INSERT INTO identity_audit_event(actor_user_id,actor_type,action,target_type,target_id,organization_id,old_value,new_value,reason,request_id) VALUES (?,'USER',?,?,?,?,?,?,?,?)",userId,action,type,target.toString(),orgId,before,after,reason,UUID.randomUUID());
    }
    private static ResourceNotFoundException missing() { return new ResourceNotFoundException("해당 조직의 건 또는 업체를 찾을 수 없습니다."); }
    private static BusinessException invalid() { return new BusinessException("유효하지 않거나 만료된 접속 링크입니다."); }
}

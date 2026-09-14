package com.productresearch.apiserver.domain.platform.entity;

import com.productresearch.apiserver.domain.identity.entity.*;
import com.productresearch.apiserver.domain.partner.entity.BusinessPartner;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "identity_audit_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityAuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "actor_user_id") private AppUser actor;
    @Column(name = "actor_type", nullable = false) private String actorType;
    @Column(nullable = false) private String action;
    @Column(name = "target_type", nullable = false) private String targetType;
    @Column(name = "target_id", nullable = false) private String targetId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organization_id") private Organization organization;
    @Column(name = "old_value") private String oldValue;
    @Column(name = "new_value") private String newValue;
    @Column(nullable = false) private String reason;
    @Column(name = "request_id", nullable = false) private UUID requestId;
    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;

    public static IdentityAuditEvent bootstrap(AppUser target) {
        IdentityAuditEvent event = new IdentityAuditEvent();
        event.actorType = "BOOTSTRAP";
        event.action = "SYSTEM_ADMIN_GRANTED";
        event.targetType = "APP_USER";
        event.targetId = target.getPublicId().toString();
        event.newValue = "SYSTEM_ADMIN";
        event.reason = "Explicit operator bootstrap command";
        event.requestId = UUID.randomUUID();
        event.occurredAt = LocalDateTime.now();
        return event;
    }
    public static IdentityAuditEvent membership(AppUser actor, OrganizationMember member, String before, String reason) {
        IdentityAuditEvent event = new IdentityAuditEvent();
        event.actor = actor; event.actorType = "USER";
        event.action = before == null ? "MEMBER_ADDED" : "MEMBER_CHANGED";
        event.targetType = "ORGANIZATION_MEMBER"; event.targetId = member.getId().toString();
        event.organization = member.getOrganization(); event.oldValue = before;
        event.newValue = member.getMemberRole().name() + ":" + member.getStatus().name();
        event.reason = reason; event.requestId = UUID.randomUUID(); event.occurredAt = LocalDateTime.now();
        return event;
    }
    public static IdentityAuditEvent application(AppUser actor, UUID applicationId, String before, String after, String reason, Organization organization) {
        IdentityAuditEvent event = new IdentityAuditEvent();
        event.actor = actor; event.actorType = "USER"; event.action = "APPLICATION_" + after;
        event.targetType = "ORGANIZATION_APPLICATION"; event.targetId = applicationId.toString();
        event.oldValue = before; event.newValue = after; event.reason = reason;
        event.organization = organization; event.requestId = UUID.randomUUID(); event.occurredAt = LocalDateTime.now();
        return event;
    }

    public static IdentityAuditEvent businessPartner(AppUser actor, BusinessPartner partner) {
        IdentityAuditEvent event = new IdentityAuditEvent();
        event.actor = actor; event.actorType = "USER"; event.action = "BUSINESS_PARTNER_CREATED";
        event.targetType = "BUSINESS_PARTNER"; event.targetId = partner.getPublicId().toString();
        event.organization = partner.getOwnerOrganization();
        event.newValue = partner.getRoles().toString(); event.reason = "거래처 등록";
        event.requestId = UUID.randomUUID(); event.occurredAt = LocalDateTime.now();
        return event;
    }
}

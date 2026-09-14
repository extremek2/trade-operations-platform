package com.tradeoperationsplatform.apiserver.domain.auth.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshSession {
    public enum Kind { ACCOUNT, ORGANIZATION, PLATFORM, CASE }
    @Column(name = "participant_id") private Long participantId;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId = UUID.randomUUID();
    @Enumerated(EnumType.STRING) @Column(name = "session_kind", nullable = false) private Kind sessionKind;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "organization_id") private Organization organization;
    @Column(name = "token_hash", nullable = false, unique = true) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;
    @Column(name = "last_used_at") private LocalDateTime lastUsedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;

    public RefreshSession(AppUser user, Organization organization, String tokenHash, LocalDateTime expiresAt) {
        this.sessionKind=Kind.ORGANIZATION; this.user=user; this.organization=organization; this.tokenHash=tokenHash; this.expiresAt=expiresAt;
    }
    public RefreshSession(AppUser user, Organization organization, Kind kind, String tokenHash, LocalDateTime expiresAt) {
        if ((kind == Kind.ORGANIZATION) != (organization != null)) throw new IllegalArgumentException("세션 컨텍스트가 올바르지 않습니다.");
        this.user = user; this.organization = organization; this.sessionKind = kind;
        this.tokenHash = tokenHash; this.expiresAt = expiresAt;
    }
    public void rotate(String tokenHash) { this.tokenHash = tokenHash; used(); }
    public static RefreshSession forCase(AppUser user, Long participantId, String hash, LocalDateTime expires) {
        RefreshSession session = new RefreshSession(user, null, Kind.CASE, hash, expires);
        session.participantId = java.util.Objects.requireNonNull(participantId);
        return session;
    }
    public boolean isUsable(LocalDateTime now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke() { revokedAt = LocalDateTime.now(); }
    public void used() { lastUsedAt = LocalDateTime.now(); }
    @PrePersist void create() { createdAt = LocalDateTime.now(); }
}

package com.tradeoperationsplatform.apiserver.domain.platform.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "platform_role_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformRoleAssignment {
    public enum Role { SYSTEM_ADMIN }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") private AppUser user;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    @Column(nullable = false) private boolean active;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "granted_by") private AppUser grantedBy;
    @Column(name = "granted_at", nullable = false) private LocalDateTime grantedAt;
    @Column(name = "revoked_at") private LocalDateTime revokedAt;

    public PlatformRoleAssignment(AppUser user) {
        this.user = user;
        this.role = Role.SYSTEM_ADMIN;
        this.active = true;
        this.grantedAt = LocalDateTime.now();
    }
    public void revoke() { active = false; revokedAt = LocalDateTime.now(); }
}

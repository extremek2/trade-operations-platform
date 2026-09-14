package com.tradeoperationsplatform.apiserver.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "organization_member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationMember {
    public enum Role { OWNER, ADMIN, OPERATOR, VIEWER }
    public enum Status { ACTIVE, INACTIVE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version @Column(nullable = false)
    private long version;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id")
    private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private AppUser user;
    @Enumerated(EnumType.STRING) @Column(name = "member_role", nullable = false)
    private Role memberRole;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    public OrganizationMember(Organization organization, AppUser user, Role role) {
        this.organization = organization;
        this.user = user;
        this.memberRole = role;
        this.status = Status.ACTIVE;
    }

    @PrePersist void create() { joinedAt = LocalDateTime.now(); }

    public void change(Role role, Status status) {
        this.memberRole = role;
        this.status = status;
    }
}

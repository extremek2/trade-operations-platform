package com.tradeoperationsplatform.apiserver.domain.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {
    public enum Status { ACTIVE, INACTIVE, INVITED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(nullable = false)
    private String name;
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private Status status;
    @Column(name = "email_verified_at") private LocalDateTime emailVerifiedAt;
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppUser(String email, String name, String phone) {
        this.publicId = UUID.randomUUID();
        this.email = email.trim().toLowerCase(java.util.Locale.ROOT);
        this.name = name;
        this.phone = phone;
        this.status = Status.INVITED;
    }

    public static AppUser registered(String email, String passwordHash, String name, String phone) {
        AppUser user = new AppUser(email, name, phone);
        user.passwordHash = passwordHash;
        user.status = Status.ACTIVE;
        return user;
    }

    public void verifyEmail() { if (emailVerifiedAt == null) emailVerifiedAt = LocalDateTime.now(); }

    public void recordLogin() { this.lastLoginAt = LocalDateTime.now(); }

    @PrePersist void create() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate void update() { updatedAt = LocalDateTime.now(); }
}

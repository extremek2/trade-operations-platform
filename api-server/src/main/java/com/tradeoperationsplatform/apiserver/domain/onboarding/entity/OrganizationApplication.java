package com.tradeoperationsplatform.apiserver.domain.onboarding.entity;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.onboarding.dto.ApplicationRequest;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationApplication {
    public enum Status { PENDING_EMAIL, PENDING_REVIEW, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId = UUID.randomUUID();
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "applicant_user_id") private AppUser applicant;
    @Column(name = "organization_name", nullable = false) private String organizationName;
    @Column(name = "business_number") private String businessNumber;
    @Column(name = "applicant_name", nullable = false) private String applicantName;
    @Column(name = "applicant_email", nullable = false) private String applicantEmail;
    private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "previous_application_id") private OrganizationApplication previous;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "approved_organization_id") private Organization approvedOrganization;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reviewed_by") private AppUser reviewedBy;
    @Column(name = "public_reason") private String publicReason;
    @Column(name = "internal_note") private String internalNote;
    @Column(name = "submitted_at", nullable = false) private LocalDateTime submittedAt = LocalDateTime.now();
    @Column(name = "reviewed_at") private LocalDateTime reviewedAt;
    @Version private Long version;

    public OrganizationApplication(AppUser user, ApplicationRequest request, OrganizationApplication previous) {
        this.applicant = user; this.organizationName = request.organizationName().trim();
        this.businessNumber = request.businessNumber(); this.applicantName = user.getName();
        this.applicantEmail = user.getEmail(); this.phone = request.phone(); this.previous = previous;
        this.status = user.getEmailVerifiedAt() == null ? Status.PENDING_EMAIL : Status.PENDING_REVIEW;
    }
    public void emailVerified() { if (status == Status.PENDING_EMAIL) status = Status.PENDING_REVIEW; }
    public void decide(AppUser reviewer, Organization organization, String reason, String note) {
        status = organization == null ? Status.REJECTED : Status.APPROVED;
        approvedOrganization = organization; reviewedBy = reviewer;
        publicReason = reason.trim(); internalNote = note; reviewedAt = LocalDateTime.now();
    }
}

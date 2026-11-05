package com.tradeoperationsplatform.apiserver.domain.onboarding.dto;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication;
import java.time.LocalDateTime;
import java.util.UUID;
public record ApplicationResponse(UUID applicationId, String organizationName, String businessNumber,
        String applicantName, String applicantEmail, String phone, OrganizationApplication.Status status,
        Long version, UUID previousApplicationId, UUID approvedOrganizationId, String reason,
        LocalDateTime submittedAt, LocalDateTime reviewedAt) {
    public static ApplicationResponse from(OrganizationApplication a) {
        return new ApplicationResponse(a.getPublicId(), a.getOrganizationName(), a.getBusinessNumber(),
                a.getApplicantName(), a.getApplicantEmail(), a.getPhone(), a.getStatus(), a.getVersion(),
                a.getPrevious() == null ? null : a.getPrevious().getPublicId(),
                a.getApprovedOrganization() == null ? null : a.getApprovedOrganization().getPublicId(),
                a.getPublicReason(), a.getSubmittedAt(), a.getReviewedAt());
    }
    public record AdminDetail(ApplicationResponse application, String internalNote, UUID reviewerId) {}
}

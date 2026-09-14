package com.tradeoperationsplatform.apiserver.domain.onboarding.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.identity.repository.*;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication.Status;
import com.tradeoperationsplatform.apiserver.domain.onboarding.repository.ApplicationRepository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.dto.*;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import com.tradeoperationsplatform.apiserver.domain.mail.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {
    private final ApplicationRepository applications;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final OrganizationMemberRepository members;
    private final IdentityAuditRepository audit;
    private final CurrentActor actor;
    private final EntityManager entityManager;
    private final MailOutbox outbox;

    @Transactional
    public ApplicationResponse submit(ApplicationRequest request) {
        return ApplicationResponse.from(submitFor(actor.access().session().getUser(), request));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrganizationApplication submitFor(AppUser applicant, ApplicationRequest request) {
        AppUser user = lockUser(applicant.getId());
        if (!applications.findByApplicantIdAndStatusIn(user.getId(), List.of(Status.PENDING_EMAIL, Status.PENDING_REVIEW)).isEmpty())
            throw new ConflictException("처리 중인 조직 개설 신청이 있습니다.");
        if (applications.existsByApplicantIdAndStatus(user.getId(), Status.APPROVED)
                || members.findAllByUserIdAndStatus(user.getId(), OrganizationMember.Status.ACTIVE).stream().anyMatch(m -> m.getMemberRole() == OrganizationMember.Role.OWNER))
            throw new ConflictException("이미 소유한 조직이 있습니다. 추가 조직 개설은 별도 운영 절차가 필요합니다.");
        OrganizationApplication previous = applications.findFirstByApplicantIdOrderByIdDesc(user.getId()).orElse(null);
        if (previous == null && request.previousApplicationId() != null) throw new ResourceNotFoundException("이전 신청을 찾을 수 없습니다.");
        if (previous != null && (previous.getStatus() != Status.REJECTED || !previous.getPublicId().equals(request.previousApplicationId())))
            throw new ConflictException("가장 최근 반려된 신청을 지정하여 재신청해 주세요.");
        var application = applications.saveAndFlush(new OrganizationApplication(user, request, previous));
        audit.save(IdentityAuditEvent.application(user, application.getPublicId(), null, application.getStatus().name(), "조직 개설 신청", null));
        return application;
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> mine(int page, int size) {
        return applications.findByApplicantId(actor.access().session().getUser().getId(), paging(page, size)).map(ApplicationResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationResponse> queue(Status status, int page, int size) {
        requireAdmin();
        return (status == null ? applications.findAll(paging(page, size)) : applications.findByStatus(status, paging(page, size))).map(ApplicationResponse::from);
    }

    @Transactional(readOnly = true)
    public ApplicationResponse.AdminDetail detail(UUID id) {
        requireAdmin();
        return detailOf(require(id));
    }

    @Transactional
    public ApplicationResponse.AdminDetail decide(UUID id, DecisionRequest request, boolean approve) {
        AppUser reviewer = requireAdmin();
        Long applicantId = applications.findApplicantId(id).orElseThrow(() -> new ResourceNotFoundException("신청을 찾을 수 없습니다."));
        AppUser applicant = lockUser(applicantId);
        var application = applications.lockByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("신청을 찾을 수 없습니다."));
        entityManager.refresh(application);
        if (application.getStatus() != Status.PENDING_REVIEW || !Objects.equals(application.getVersion(), request.version()))
            throw new ConflictException("이미 처리되었거나 변경된 신청입니다. 최신 정보를 확인해 주세요.");
        if (applicant.getEmailVerifiedAt() == null || !applicant.getEmail().equals(application.getApplicantEmail()))
            throw new ConflictException("신청 이메일 확인이 필요합니다.");
        Organization organization = null;
        if (approve) {
            if (!members.findAllByUserIdAndStatus(applicant.getId(), OrganizationMember.Status.ACTIVE).isEmpty())
                throw new ConflictException("이미 조직에 소속된 신청자입니다. 기존 소속을 확인한 후 처리해 주세요.");
            organization = organizations.save(new Organization(application.getOrganizationName(), Organization.Type.SHIPPER,
                    application.getBusinessNumber(), application.getApplicantEmail(), application.getPhone()));
            members.save(new OrganizationMember(organization, applicant, OrganizationMember.Role.OWNER));
        }
        application.decide(reviewer, organization, request.reason(), request.internalNote());
        audit.save(IdentityAuditEvent.application(reviewer, id, Status.PENDING_REVIEW.name(), application.getStatus().name(), request.reason(), organization));
        outbox.enqueue(null, new MailMessage(applicant.getEmail(), "조직 개설 신청 처리 안내",
                "조직 개설 신청이 " + (approve ? "승인" : "반려") + "되었습니다. 로그인하여 신청 상태를 확인해 주세요."), LocalDateTime.now().plusDays(7));
        applications.flush();
        return detailOf(application);
    }

    private AppUser requireAdmin() {
        var access = actor.access();
        if (access.session().getSessionKind() != RefreshSession.Kind.PLATFORM) throw new AccessDeniedException("시스템관리자 권한이 필요합니다.");
        return access.session().getUser();
    }
    private AppUser lockUser(Long id) {
        AppUser user = users.lockById(id).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        entityManager.refresh(user);
        if (user.getStatus() != AppUser.Status.ACTIVE) throw new ConflictException("비활성 신청자는 처리할 수 없습니다.");
        return user;
    }
    private OrganizationApplication require(UUID id) {
        return applications.findByPublicId(id).orElseThrow(() -> new ResourceNotFoundException("신청을 찾을 수 없습니다."));
    }
    private ApplicationResponse.AdminDetail detailOf(OrganizationApplication a) {
        return new ApplicationResponse.AdminDetail(ApplicationResponse.from(a), a.getInternalNote(), a.getReviewedBy() == null ? null : a.getReviewedBy().getPublicId());
    }
    private Pageable paging(int page, int size) {
        return PageRequest.of(Math.max(0, page), Math.max(1, Math.min(100, size)), Sort.by(Sort.Direction.DESC, "submittedAt", "id"));
    }
}

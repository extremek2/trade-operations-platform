package com.tradeoperationsplatform.apiserver.domain.onboarding.repository;
import com.tradeoperationsplatform.apiserver.domain.onboarding.entity.OrganizationApplication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface ApplicationRepository extends JpaRepository<OrganizationApplication, Long> {
    Optional<OrganizationApplication> findByPublicId(UUID id);
    @Query("select a.applicant.id from OrganizationApplication a where a.publicId=:id")
    Optional<Long> findApplicantId(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from OrganizationApplication a where a.publicId=:id")
    Optional<OrganizationApplication> lockByPublicId(@Param("id") UUID id);
    List<OrganizationApplication> findByApplicantIdAndStatusIn(Long userId, Collection<OrganizationApplication.Status> statuses);
    Page<OrganizationApplication> findByApplicantId(Long userId, Pageable page);
    Page<OrganizationApplication> findByStatus(OrganizationApplication.Status status, Pageable page);
    boolean existsByApplicantIdAndStatus(Long userId, OrganizationApplication.Status status);
    Optional<OrganizationApplication> findFirstByApplicantIdOrderByIdDesc(Long userId);
}

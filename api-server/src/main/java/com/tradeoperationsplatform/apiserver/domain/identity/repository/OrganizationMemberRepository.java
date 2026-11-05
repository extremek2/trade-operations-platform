package com.tradeoperationsplatform.apiserver.domain.identity.repository;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    org.springframework.data.domain.Page<OrganizationMember> findAllByOrganizationId(Long organizationId, org.springframework.data.domain.Pageable page);
    Optional<OrganizationMember> findByOrganizationIdAndUserPublicId(Long organizationId, UUID userId);
    boolean existsByOrganizationIdAndUserIdAndStatus(Long organizationId, Long userId, OrganizationMember.Status status);
    List<OrganizationMember> findAllByUserIdAndStatus(Long userId, OrganizationMember.Status status);
    Optional<OrganizationMember> findByUserIdAndOrganizationIdAndStatus(Long userId, Long organizationId, OrganizationMember.Status status);
}

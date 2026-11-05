package com.tradeoperationsplatform.apiserver.domain.platform.repository;

import com.tradeoperationsplatform.apiserver.domain.platform.entity.PlatformRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformRoleRepository extends JpaRepository<PlatformRoleAssignment, Long> {
    boolean existsByUserIdAndRoleAndActiveTrue(Long userId, PlatformRoleAssignment.Role role);
    boolean existsByUserIdAndRole(Long userId, PlatformRoleAssignment.Role role);
}

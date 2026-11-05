package com.tradeoperationsplatform.apiserver.domain.auth.repository;

import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, Long> {
    @Modifying(flushAutomatically = true)
    @Query("update RefreshSession s set s.revokedAt=:now where s.user.id=:userId and s.organization.id=:organizationId and s.revokedAt is null")
    int revokeOrganizationSessions(@Param("userId") Long userId, @Param("organizationId") Long organizationId,
                                  @Param("now") java.time.LocalDateTime now);
    Optional<RefreshSession> findByTokenHash(String tokenHash);
    Optional<RefreshSession> findByPublicId(UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.publicId = :publicId")
    Optional<RefreshSession> findLockedByPublicId(@Param("publicId") UUID publicId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash = :hash")
    Optional<RefreshSession> findLockedByTokenHash(@Param("hash") String hash);
}

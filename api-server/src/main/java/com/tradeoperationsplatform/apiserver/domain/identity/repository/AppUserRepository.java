package com.tradeoperationsplatform.apiserver.domain.identity.repository;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id=:id")
    Optional<AppUser> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<AppUser> findByPublicId(UUID publicId);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}

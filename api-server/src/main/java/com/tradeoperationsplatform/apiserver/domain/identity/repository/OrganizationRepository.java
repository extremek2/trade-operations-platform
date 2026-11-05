package com.tradeoperationsplatform.apiserver.domain.identity.repository;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from Organization o where o.id=:id")
    Optional<Organization> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<Organization> findByPublicId(UUID publicId);
}

package com.tradeoperationsplatform.apiserver.domain.platform.repository;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IdentityAuditRepository extends JpaRepository<IdentityAuditEvent, Long> {}

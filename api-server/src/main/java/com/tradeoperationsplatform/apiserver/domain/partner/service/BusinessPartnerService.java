package com.tradeoperationsplatform.apiserver.domain.partner.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.dto.*;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessPartnerService {
    private final BusinessPartnerRepository partners;
    private final IdentityAuditRepository audit;
    private final CurrentActor currentActor;

    @Transactional(readOnly = true)
    public List<BusinessPartnerResponse> findAll() {
        var actor = currentActor.require();
        return partners.findAllOwnedWithRoles(actor.organization().getId()).stream()
                .map(BusinessPartnerResponse::from).toList();
    }

    @Transactional
    public BusinessPartnerResponse create(BusinessPartnerRequests.Create request) {
        var actor = currentActor.require();
        String name = request.name().trim();
        if (partners.existsOwnedName(actor.organization().getId(), name)) {
            throw new ConflictException("이미 등록된 거래처명입니다.");
        }
        var roles = request.roles().iterator();
        BusinessPartner partner = new BusinessPartner(actor.organization(), name, request.countryCode(),
                request.businessNumber(), roles.next());
        roles.forEachRemaining(partner::addRole);
        partners.save(partner);
        audit.save(IdentityAuditEvent.businessPartner(actor.user(), partner));
        return BusinessPartnerResponse.from(partner);
    }
}

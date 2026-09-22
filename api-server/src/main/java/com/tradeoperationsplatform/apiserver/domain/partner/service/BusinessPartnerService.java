package com.tradeoperationsplatform.apiserver.domain.partner.service;

import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.partner.dto.*;
import com.tradeoperationsplatform.apiserver.domain.partner.entity.BusinessPartner;
import com.tradeoperationsplatform.apiserver.domain.partner.repository.BusinessPartnerRepository;
import com.tradeoperationsplatform.apiserver.global.exception.ConflictException;
import com.tradeoperationsplatform.apiserver.global.exception.BusinessException;
import com.tradeoperationsplatform.apiserver.global.exception.ResourceNotFoundException;
import com.tradeoperationsplatform.apiserver.domain.platform.entity.IdentityAuditEvent;
import com.tradeoperationsplatform.apiserver.domain.platform.repository.IdentityAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

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
        if (request.inquiryEmail() != null || request.inquiryCcEmails() != null)
            applyInquiryContacts(partner, request.inquiryEmail(), request.inquiryCcEmails());
        partners.save(partner);
        audit.save(IdentityAuditEvent.businessPartner(actor.user(), partner));
        return BusinessPartnerResponse.from(partner);
    }

    @Transactional
    public BusinessPartnerResponse updateInquiryContacts(UUID partnerId, BusinessPartnerRequests.InquiryContacts request) {
        var actor = currentActor.require();
        BusinessPartner partner = partners.findOwnedWithRoles(partnerId, actor.organization().getId())
                .orElseThrow(() -> new ResourceNotFoundException("거래처를 찾을 수 없습니다."));
        if (partner.getVersion() != request.version())
            throw new ConflictException("거래처 정보가 변경되었습니다. 목록을 새로고침해 주세요.");
        applyInquiryContacts(partner, request.inquiryEmail(), request.inquiryCcEmails());
        partners.flush();
        return BusinessPartnerResponse.from(partner);
    }

    private void applyInquiryContacts(BusinessPartner partner, String email, List<String> ccEmails) {
        try {
            partner.setInquiryContacts(email, ccEmails);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }
}

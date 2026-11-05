package com.tradeoperationsplatform.apiserver.domain.collaboration;

import com.tradeoperationsplatform.apiserver.domain.auth.entity.RefreshSession;
import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.shipment.dto.*;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.TransportDocument;
import com.tradeoperationsplatform.apiserver.domain.shipment.repository.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CaseWorkspaceService {
    private final CurrentActor actor;
    private final CaseAccess caseAccess;
    private final JdbcTemplate jdbc;
    private final ShipmentCaseRepository shipments;
    private final TransportDocumentRepository documents;

    @Transactional(readOnly=true)
    public Map<String,Object> read(UUID shipmentId) {
        var scope=scope(shipmentId);
        // Explicitly expose only the operational fields needed for this case, not shipper references or other cases.
        var result=jdbc.queryForList("""
            SELECT public_id AS "shipmentId",case_number AS "caseNumber",transport_mode AS "transportMode",
            current_stage AS "currentStage",origin_location_code AS "origin",destination_location_code AS "destination",
            carrier_name AS "carrierName",vessel_name AS "vesselName",flight_number AS "flightNumber",etd,eta,cargo_description AS "cargoDescription"
            FROM shipment_case WHERE id=?
            """,scope.shipmentId()).get(0);
        result.put("accessLevel",scope.accessLevel());
        result.put("documents",documents.findAllByShipmentCaseIdOrderByCreatedAtAsc(scope.shipmentId()).stream().map(TransportDocumentResponse::from).toList());
        return result;
    }
    @Transactional
    public TransportDocumentResponse addDocument(UUID shipmentId,CreateTransportDocumentRequest request) {
        var initial=scope(shipmentId);
        // The invitation/revocation path uses the same organization -> shipment lock order.
        var orgId=jdbc.queryForObject("SELECT owner_organization_id FROM shipment_case WHERE id=?",Long.class,initial.shipmentId());
        jdbc.queryForList("SELECT id FROM organization WHERE id=? FOR UPDATE",orgId);
        jdbc.queryForList("SELECT id FROM shipment_case WHERE id=? FOR UPDATE",initial.shipmentId());
        var scope=scope(shipmentId);
        if(!"CONTRIBUTOR".equals(scope.accessLevel())) throw new AccessDeniedException("운송 문서번호 등록 권한이 필요합니다.");
        if(request.primary()) throw new BusinessException("외부 참여자는 대표 문서를 지정할 수 없습니다.");
        if(documents.existsByShipmentCaseIdAndDocumentTypeAndDocumentNumber(scope.shipmentId(),request.documentType(),request.documentNumber())) throw new ConflictException("이미 등록된 운송 문서번호입니다.");
        var shipment=shipments.findById(scope.shipmentId()).orElseThrow();
        var document=documents.saveAndFlush(new TransportDocument(shipment,request.documentType(),request.documentNumber(),request.issuerName(),request.issuedAt(),false));
        jdbc.update("INSERT INTO identity_audit_event(actor_user_id,actor_type,action,target_type,target_id,organization_id,new_value,reason,request_id) VALUES (?,'USER','CASE_DOCUMENT_ADDED','TRANSPORT_DOCUMENT',?,?,?,'외부 담당자 운송 문서번호 등록',?)",
                actor.access().session().getUser().getId(),document.getPublicId().toString(),orgId,request.documentType().name(),UUID.randomUUID());
        return TransportDocumentResponse.from(document);
    }
    private CaseAccess.Scope scope(UUID shipmentId) {
        var access=actor.access();
        if(access.session().getSessionKind()!=RefreshSession.Kind.CASE) throw new AccessDeniedException("건별 접속 권한이 필요합니다.");
        var scope=caseAccess.require(access.session().getParticipantId(),access.session().getUser().getId());
        if(!scope.shipmentPublicId().equals(shipmentId)) throw new ResourceNotFoundException("참여한 건을 찾을 수 없습니다.");
        return scope;
    }
}

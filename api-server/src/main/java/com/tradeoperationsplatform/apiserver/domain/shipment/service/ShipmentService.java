package com.tradeoperationsplatform.apiserver.domain.shipment.service;

import com.tradeoperationsplatform.apiserver.domain.identity.entity.*;
import com.tradeoperationsplatform.apiserver.domain.auth.service.CurrentActor;
import com.tradeoperationsplatform.apiserver.domain.shipment.dto.*;
import com.tradeoperationsplatform.apiserver.domain.shipment.entity.*;
import com.tradeoperationsplatform.apiserver.domain.shipment.repository.*;
import com.tradeoperationsplatform.apiserver.global.exception.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentCaseRepository shipmentRepository;
    private final TransportDocumentRepository documentRepository;
    private final CurrentActor currentActor;

    @Transactional
    public ShipmentResponse create(CreateShipmentRequest request) {
        CurrentActor.Context actor = currentActor.require();
        Organization organization = actor.organization();
        AppUser creator = actor.user();
        if (shipmentRepository.existsByOwnerOrganizationIdAndCaseNumber(organization.getId(), request.caseNumber())) {
            throw new BusinessException("조직 내에서 이미 사용 중인 화물 관리번호입니다.");
        }
        ShipmentCase shipment = new ShipmentCase(organization, creator, request.caseNumber(), request.direction(),
                request.transportMode(), request.currentStage(), request.priority());
        shipment.fillDetails(request.shipperReference(), request.purchaseOrderNumber(), request.originLocationCode(),
                request.originLocationName(), request.destinationLocationCode(), request.destinationLocationName(),
                request.cargoDescription(), request.packageCount(), request.grossWeight(), request.weightUnit(),
                request.containerCount(), request.carrierName(), request.vesselName(), request.voyageNumber(),
                request.flightNumber(), request.etd(), request.eta());
        return response(shipmentRepository.save(shipment), false);
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> findAll(ShipmentCase.Status status, ShipmentCase.Priority priority,
                                          ShipmentCase.Stage stage, boolean includeArchived) {
        UUID organizationId = currentActor.require().organization().getPublicId();
        Specification<ShipmentCase> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("ownerOrganization").get("publicId"), organizationId));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (stage != null) predicates.add(cb.equal(root.get("currentStage"), stage));
            if (!includeArchived) predicates.add(cb.isNull(root.get("archivedAt")));
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return shipmentRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "eta").and(Sort.by(Sort.Direction.DESC, "createdAt")))
                .stream().map(shipment -> response(shipment, false)).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findOne(UUID shipmentId) {
        return response(requireShipment(shipmentId), true);
    }

    @Transactional
    public ShipmentResponse update(UUID shipmentId, UpdateShipmentRequest request) {
        ShipmentCase shipment = requireShipment(shipmentId);
        if (!Objects.equals(shipment.getVersion(), request.version())) {
            throw new BusinessException("다른 사용자가 먼저 수정했습니다. 최신 데이터를 다시 조회해 주세요.");
        }
        if (shipment.getArchivedAt() != null) throw new BusinessException("보관된 화물은 수정할 수 없습니다.");
        try {
            shipment.update(request.currentStage(), request.status(), request.priority(), request.carrierName(),
                    request.vesselName(), request.voyageNumber(), request.flightNumber(), request.etd(), request.atd(),
                    request.eta(), request.ata(), request.cargoDescription());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        return response(shipment, true);
    }

    @Transactional
    public ShipmentResponse archive(UUID shipmentId) {
        ShipmentCase shipment = requireShipment(shipmentId);
        shipment.archive();
        return response(shipment, true);
    }

    @Transactional
    public TransportDocumentResponse addDocument(UUID shipmentId, CreateTransportDocumentRequest request) {
        ShipmentCase shipment = requireShipment(shipmentId);
        if (shipment.getArchivedAt() != null) throw new BusinessException("보관된 화물에는 문서를 추가할 수 없습니다.");
        if (documentRepository.existsByShipmentCaseIdAndDocumentTypeAndDocumentNumber(
                shipment.getId(), request.documentType(), request.documentNumber())) {
            throw new BusinessException("동일한 유형과 번호의 운송 문서가 이미 존재합니다.");
        }
        return TransportDocumentResponse.from(documentRepository.save(new TransportDocument(shipment,
                request.documentType(), request.documentNumber(), request.issuerName(), request.issuedAt(), request.primary())));
    }

    @Transactional(readOnly = true)
    public List<TransportDocumentResponse> findDocuments(UUID shipmentId) {
        ShipmentCase shipment = requireShipment(shipmentId);
        return documentRepository.findAllByShipmentCaseIdOrderByCreatedAtAsc(shipment.getId())
                .stream().map(TransportDocumentResponse::from).toList();
    }

    private ShipmentCase requireShipment(UUID shipmentId) {
        UUID organizationId = currentActor.require().organization().getPublicId();
        return shipmentRepository.findByPublicIdAndOwnerOrganizationPublicId(shipmentId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 조직의 화물을 찾을 수 없습니다."));
    }

    private ShipmentResponse response(ShipmentCase shipment, boolean includeDocuments) {
        List<TransportDocumentResponse> documents = includeDocuments
                ? documentRepository.findAllByShipmentCaseIdOrderByCreatedAtAsc(shipment.getId()).stream().map(TransportDocumentResponse::from).toList()
                : List.of();
        return ShipmentResponse.of(shipment, documents);
    }
}

package com.tradeoperationsplatform.apiserver.domain.shipment.repository;

import com.tradeoperationsplatform.apiserver.domain.shipment.entity.TransportDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransportDocumentRepository extends JpaRepository<TransportDocument, Long> {
    List<TransportDocument> findAllByShipmentCaseIdOrderByCreatedAtAsc(Long shipmentCaseId);
    boolean existsByShipmentCaseIdAndDocumentTypeAndDocumentNumber(Long shipmentCaseId, TransportDocument.Type type, String number);
}

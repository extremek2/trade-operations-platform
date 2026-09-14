package com.productresearch.apiserver.domain.collaboration;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.UUID;
import static com.productresearch.apiserver.domain.auth.service.SessionAccessService.invalid;

@Service
@RequiredArgsConstructor
public class CaseAccess {
    private final JdbcTemplate jdbc;
    public Scope require(Long participantId, Long userId) {
        if (participantId == null) throw invalid();
        var rows = jdbc.query("""
            SELECT p.id,s.id AS shipment_id,s.public_id,p.public_id AS participant_public_id,p.access_level
            FROM case_participant p JOIN case_partner cp ON cp.id=p.case_partner_id AND cp.shipment_case_id=p.shipment_case_id
            JOIN business_partner c ON c.id=cp.business_partner_id
            JOIN shipment_case s ON s.id=p.shipment_case_id AND s.owner_organization_id=c.owner_organization_id
            JOIN organization o ON o.id=s.owner_organization_id
            WHERE p.id=? AND p.user_id=? AND p.status='ACTIVE' AND p.access_level IN ('VIEWER','CONTRIBUTOR')
              AND o.status='ACTIVE' AND s.archived_at IS NULL
            """, (r,n) -> new Scope(r.getLong("id"),r.getLong("shipment_id"),r.getObject("public_id",UUID.class),
                    r.getObject("participant_public_id",UUID.class),r.getString("access_level")),participantId,userId);
        if (rows.size()!=1) throw invalid();
        return rows.get(0);
    }
    public record Scope(Long participantId,Long shipmentId,UUID shipmentPublicId,UUID participantPublicId,String accessLevel) {}
}

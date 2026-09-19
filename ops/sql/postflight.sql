\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT version,success FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7','8','9','10') ORDER BY installed_rank;
SELECT count(*) AS active_system_admins FROM platform_role_assignment WHERE role='SYSTEM_ADMIN' AND active;
SELECT session_kind,count(*) FROM refresh_session WHERE revoked_at IS NULL AND expires_at>NOW() GROUP BY session_kind;
SELECT status,count(*) FROM organization_application GROUP BY status;
SELECT count(*) AS over_capacity_partners FROM (
  SELECT p.case_partner_id FROM case_participant p WHERE p.case_partner_id IS NOT NULL AND
    (p.status='ACTIVE' OR (p.status='INVITED' AND EXISTS(SELECT 1 FROM case_invitation i WHERE i.participant_id=p.id AND i.invitation_status='PENDING' AND i.expires_at>NOW())))
  GROUP BY p.case_partner_id HAVING count(*)>3
) violations;
SELECT status,count(*),min(created_at) AS oldest_created_at FROM mail_outbox GROUP BY status;
DO $$ BEGIN
  IF (SELECT count(*) FROM flyway_schema_history WHERE version IN ('1','2','3','4','5','6','7','8','9','10') AND success)<>10 THEN
    RAISE EXCEPTION 'Expected application migrations V1-V10';
  END IF;
END $$;
COMMIT;

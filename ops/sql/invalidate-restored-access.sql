\set ON_ERROR_STOP on
-- Run ONLY on a restored application database, with traffic and mail still stopped.
-- Prevent backup copies of sessions and links from authenticating or sending again.
BEGIN;
UPDATE refresh_session SET revoked_at=NOW() WHERE revoked_at IS NULL;
UPDATE email_auth_token SET revoked_at=NOW() WHERE consumed_at IS NULL AND revoked_at IS NULL;
UPDATE mail_outbox SET status='CANCELLED',encrypted_payload=NULL WHERE status='PENDING';
UPDATE case_invitation SET revoked_at=COALESCE(revoked_at,NOW()),invitation_status='REVOKED' WHERE revoked_at IS NULL;
UPDATE case_participant SET status='REVOKED' WHERE case_partner_id IS NOT NULL AND status<>'REVOKED';
COMMIT;
-- Reconcile account status, OWNER/ADMIN changes and business data since the backup before reopening.
-- External participants must be explicitly re-invited after that review.

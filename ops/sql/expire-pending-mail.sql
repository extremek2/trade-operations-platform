\set ON_ERROR_STOP on
-- Explicit maintenance mutation: expire only messages whose delivery deadline has passed.
BEGIN;
UPDATE mail_outbox SET status='EXPIRED',encrypted_payload=NULL
WHERE status='PENDING' AND expires_at<=NOW();
COMMIT;

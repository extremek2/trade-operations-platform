\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT current_database() AS database, current_user AS database_user,
       current_setting('server_version_num')::integer >= 150000 AS postgres_15_or_newer;
SELECT version,description,success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 6;
-- Aggregates only: no email addresses, password hashes, tokens or mail bodies are printed.
SELECT count(*) AS normalized_email_duplicate_groups FROM (
  SELECT lower(btrim(email)) FROM app_user GROUP BY lower(btrim(email)) HAVING count(*)>1
) duplicates;
SELECT count(*) AS organizations_without_active_owner FROM organization o
WHERE o.status='ACTIVE' AND NOT EXISTS (
  SELECT 1 FROM organization_member m JOIN app_user u ON u.id=m.user_id
  WHERE m.organization_id=o.id AND m.member_role='OWNER' AND m.status='ACTIVE' AND u.status='ACTIVE'
);
SELECT count(*) AS normalized_contact_duplicate_groups FROM (
  SELECT owner_organization_id,lower(btrim(email)) FROM external_contact
  GROUP BY owner_organization_id,lower(btrim(email)) HAVING count(*)>1
) duplicates;
SELECT count(*) AS accounts_with_multiple_active_memberships FROM (
  SELECT user_id FROM organization_member WHERE status='ACTIVE' GROUP BY user_id HAVING count(*)>1
) multiple_memberships;
DO $$ BEGIN
  IF current_setting('server_version_num')::integer < 150000 THEN RAISE EXCEPTION 'PostgreSQL 15 or newer required'; END IF;
  IF EXISTS(SELECT 1 FROM flyway_schema_history WHERE NOT success) THEN RAISE EXCEPTION 'Failed migration requires review'; END IF;
  IF EXISTS(SELECT 1 FROM app_user GROUP BY lower(btrim(email)) HAVING count(*)>1) THEN RAISE EXCEPTION 'Normalized user emails must be reconciled before migration'; END IF;
END $$;
COMMIT;

\set ON_ERROR_STOP on
\getenv runtime_user APP_DB_USER
\getenv runtime_password APP_DB_PASSWORD
-- Values are read from the container environment, never passed as command-line password arguments.
BEGIN;
SELECT current_user <> :'runtime_user' AS separate_role \gset
\if :separate_role
\else
  \echo 'Runtime role must differ from the migration/database owner.'
  \quit 1
\endif
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', :'runtime_user', :'runtime_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname=:'runtime_user') \gexec
-- Existing runtime passwords are not silently reset. Credential rotation is a separate operation.
SELECT NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole AND NOT rolreplication AS restricted_role
FROM pg_roles WHERE rolname=:'runtime_user' \gset
\if :restricted_role
\else
  \echo 'Existing runtime role has elevated privileges; review it before continuing.'
  \quit 1
\endif
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), :'runtime_user') \gexec
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'runtime_user') \gexec
SELECT format('GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO %I', :'runtime_user') \gexec
SELECT format('GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'runtime_user') \gexec
SELECT format('REVOKE INSERT,UPDATE,DELETE ON flyway_schema_history FROM %I', :'runtime_user') \gexec
SELECT format('REVOKE UPDATE,DELETE ON identity_audit_event FROM %I', :'runtime_user') \gexec
COMMIT;

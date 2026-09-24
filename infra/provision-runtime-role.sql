\set ON_ERROR_STOP on

-- Run as a database administrator after the database exists and before the app
-- is switched to DATABASE_RUNTIME_USER. Pass all variables with psql -v.
-- The migration role owns the app schema, but is neither superuser nor BYPASSRLS.
-- Example (never put literal production secrets in shell history):
--   psql "$DATABASE_ADMIN_URL" \
--     -v runtime_role=whatplan_runtime \
--     -v runtime_password="$DATABASE_RUNTIME_PASSWORD" \
--     -v migration_role=whatplan_migrator \
--     -v migration_password="$DATABASE_MIGRATION_PASSWORD" \
--     -v database_name=wherefood \
--     -f infra/provision-runtime-role.sql

SELECT :'runtime_role' <> :'migration_role'
   AND :'runtime_role' <> current_user
   AND :'migration_role' <> current_user AS role_names_are_safe \gset
\if :role_names_are_safe
\else
    \echo Runtime and migration roles must be distinct from each other and the connected administrator
    \quit 3
\endif

SELECT current_database() = :'database_name' AS database_name_matches \gset
\if :database_name_matches
\else
    \echo The connected database does not match database_name
    \quit 3
\endif

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'migration_role',
    :'migration_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'migration_role'
)\gexec

SELECT format(
    'ALTER ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'migration_role',
    :'migration_password'
)\gexec

SELECT format(
    'CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'runtime_role',
    :'runtime_password'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'runtime_role'
)\gexec

SELECT format(
    'ALTER ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOBYPASSRLS',
    :'runtime_role',
    :'runtime_password'
)\gexec

-- Remove pre-existing memberships that could grant extra or SET ROLE privileges.
SELECT format('REVOKE %I FROM %I', granted.rolname, member.rolname)
FROM pg_auth_members membership
JOIN pg_roles granted ON granted.oid = membership.roleid
JOIN pg_roles member ON member.oid = membership.member
WHERE member.rolname IN (:'runtime_role', :'migration_role')\gexec

GRANT CONNECT ON DATABASE :"database_name" TO :"migration_role", :"runtime_role";
ALTER SCHEMA public OWNER TO :"migration_role";

-- Flyway's role owns the existing application tables and sequences. FORCE RLS
-- still applies to an owner, so future data migrations must set app.couple_id.
SELECT format('ALTER TABLE %I.%I OWNER TO %I', schemaname, tablename, :'migration_role')
FROM pg_tables
WHERE schemaname = 'public'\gexec

SELECT format('ALTER SEQUENCE %I.%I OWNER TO %I', n.nspname, c.relname, :'migration_role')
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public'
  AND c.relkind = 'S'\gexec

GRANT USAGE, CREATE ON SCHEMA public TO :"migration_role";
GRANT USAGE ON SCHEMA public TO :"runtime_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";

-- New Flyway-created tables and sequences inherit runtime DML permissions.
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_role" IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO :"runtime_role";
ALTER DEFAULT PRIVILEGES FOR ROLE :"migration_role" IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO :"runtime_role";

-- Fail the provisioning command if the effective role can bypass RLS.
SELECT coalesce(bool_or(rolsuper OR rolbypassrls), true) AS unsafe
FROM pg_roles
WHERE rolname IN (:'runtime_role', :'migration_role')\gset
\if :unsafe
    \echo Runtime and migration roles must not be superuser or BYPASSRLS
    \quit 3
\endif

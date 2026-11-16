-- Run as cluster administrator ONLY against a new, empty application database.
-- psql -X -v ON_ERROR_STOP=1 -d NEW_DATABASE -f scripts/provision-database-roles.sql
BEGIN;
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_tables WHERE schemaname='public') THEN
  RAISE EXCEPTION 'Role provisioning requires an empty public schema';
 END IF;
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname IN ('scarlet_migrator','scarlet_runtime','scarlet_backup')) THEN
  RAISE EXCEPTION 'Refusing to modify existing roles';
 END IF;
END $$;
CREATE ROLE scarlet_migrator LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
CREATE ROLE scarlet_runtime LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
CREATE ROLE scarlet_backup LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
DO $$ BEGIN
 EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC', current_database());
 EXECUTE format('GRANT CONNECT ON DATABASE %I TO scarlet_migrator,scarlet_runtime,scarlet_backup', current_database());
END $$;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
ALTER SCHEMA public OWNER TO scarlet_migrator;
GRANT USAGE ON SCHEMA public TO scarlet_runtime;
GRANT USAGE ON SCHEMA public TO scarlet_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE scarlet_migrator IN SCHEMA public GRANT SELECT ON TABLES TO scarlet_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE scarlet_migrator IN SCHEMA public GRANT SELECT ON SEQUENCES TO scarlet_backup;
ALTER DEFAULT PRIVILEGES FOR ROLE scarlet_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO scarlet_runtime;
ALTER DEFAULT PRIVILEGES FOR ROLE scarlet_migrator IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
COMMIT;
-- Set passwords separately with interactive psql \password commands or your secret manager.

-- Run once on first container start (via docker-entrypoint-initdb.d) for the
-- `hephaestus` database. Pre-creates extensions Liquibase would otherwise have to
-- create at boot, which keeps the migration phase strictly schema-only.
--
-- Add new extensions here BEFORE introducing changesets that depend on them.

CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

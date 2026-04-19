-- Mirrors the de_fold_name function created by Liquibase migration
-- 1776587029158_changelog.xml. Re-declared here because integration tests
-- run with `spring.liquibase.enabled=false` and rely on Hibernate DDL,
-- so the migration-authored function is otherwise absent from the test schema.
CREATE OR REPLACE FUNCTION de_fold_name(input text)
RETURNS text
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
    SELECT REPLACE(
               REPLACE(
                   REPLACE(
                       REPLACE(LOWER(input), 'ö', 'oe'),
                       'ä', 'ae'
                   ),
                   'ü', 'ue'
               ),
               'ß', 'ss'
           )
$$;

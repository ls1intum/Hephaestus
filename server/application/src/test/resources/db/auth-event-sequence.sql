-- auth_event's id comes from AuthEventSequence, not from Hibernate: the partitioned table's composite
-- PK (id, occurred_at) rules out an @GeneratedValue. Tests run against ddl-auto: create, which builds
-- the table but not that sequence, so every audit write would fail and be swallowed — leaving an
-- assertion on the audit trail vacuously green. Load this wherever a test asserts on auth_event rows.
CREATE SEQUENCE IF NOT EXISTS auth_event_id_seq;

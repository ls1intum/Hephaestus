-- auth_event.id is allocated by AuthEventSequence, not by Hibernate, so the schema Hibernate
-- generates for the real-auth integration tests does not contain the sequence. Without it every
-- audit write is swallowed as a failure and the trail an assertion is looking for is never there.
CREATE SEQUENCE IF NOT EXISTS auth_event_id_seq;

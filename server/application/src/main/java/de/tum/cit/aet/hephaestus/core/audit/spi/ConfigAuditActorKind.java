package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * What kind of actor caused the change. NOT NULL on the row (mirroring {@code connection_audit
 * .actor_kind}) so that a null {@code actor_account_id} is never ambiguous: without this, "a
 * background job did it", "an unauthenticated caller did it", and "we erased who did it" all collapse
 * into the same absent id, and a log that cannot tell those apart fails at its only job.
 */
public enum ConfigAuditActorKind {
    /** A signed-in human acting as themselves. */
    USER,
    /** No security context — a seeder, scheduler, or platform-event handler. */
    SYSTEM,
    /** A human acting under impersonation; both the subject and the operator are recorded. */
    IMPERSONATED,
}

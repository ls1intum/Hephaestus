package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * Records an admin configuration change on the append-only {@code config_audit_event} trail.
 *
 * <p>Answers "who changed which control, when, from what to what" — not "why did a delivery happen", which
 * lives in the resolver's persisted reason chain: mode changes resolve prospectively, so the config at posting
 * time is not the config that decided, and reconstructing a delivery from this history is confidently wrong.
 *
 * <p><b>Transactional contract:</b> joins the caller's transaction and requires one ({@code MANDATORY}) — call
 * it inside the same {@code @Transactional} method that performs the mutation, so a failed audit write rolls
 * the change back with it.
 */
public interface ConfigAuditPort {
    /**
     * Append one row describing a configuration change.
     *
     * <p>No-op updates are dropped: if {@code before} and {@code after} serialize identically, an
     * idempotent PATCH leaves no row rather than polluting the resource's history.
     *
     * @throws ConfigAuditUnavailableException if there is no active, writable transaction
     */
    void record(ConfigAuditEntry entry);
}

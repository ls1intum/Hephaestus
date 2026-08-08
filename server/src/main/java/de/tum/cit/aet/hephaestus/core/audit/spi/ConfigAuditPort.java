package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * Records an admin configuration change on the append-only {@code config_audit_event} trail.
 *
 * <p>Answers "who changed which control, when, from what to what" — accountability and change
 * forensics. It is deliberately <em>not</em> the delivery explanation: "why did user X get a comment
 * yesterday?" is answered by the resolver's persisted reason chain, evaluated at decision
 * time. Because mode changes resolve prospectively, the config at posting time is not the config that
 * decided, so reconstructing a delivery from this history yields a confidently wrong answer.
 *
 * <p><b>Transactional contract:</b> the implementation joins the caller's transaction and requires one
 * ({@code MANDATORY}), so call it from inside the same {@code @Transactional} service method that
 * performs the mutation. Unlike the auth trail — telemetry, which must never break a login and so
 * writes in its own transaction and swallows failures — this is a control: a config change that commits
 * without its audit row is the exact failure this port exists to prevent, so a failed audit write rolls
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

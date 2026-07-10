/**
 * Data-access audit — a small, generalized, append-only disclosure log.
 *
 * <p>Records one row each time a privileged actor is shown another person's (or a named cohort's) data,
 * tamper-evident at the storage layer. The first writer is the practice-report surface (a mentor opening
 * a developer's report, or the roster that names developers), but the model is deliberately resource-typed
 * ({@link DataAccessResourceType}) so any future disclosure surface can reuse it without a new bespoke table.
 *
 * <p>The application only WRITES this log: the append-only insert path ({@link DataAccessAuditWriter}),
 * the marker-guarded workspace purge, and the account-erasure redaction (actor id zeroed, subject id
 * nulled — see {@code core.auth.AccountPurger}). There is no in-app read surface; compliance and
 * subject-access requests are served manually via SQL.
 *
 * <h2>Relationship to {@code core.auth.audit.AuthEvent}</h2>
 * This is a deliberate <em>peer</em>, not a reuse, of {@code AuthEvent}. They live in different identity
 * namespaces — {@code AuthEvent.account_id} is the login {@code Account}; here {@code actor_user_id} /
 * {@code subject_user_id} are workspace-scoped SCM {@code User} ids — and record a different concern
 * (data disclosures vs. security/impersonation forensics). {@code AuthEvent} is
 * high-volume and monthly-partitioned; this disclosure log is low-volume and intentionally NOT partitioned.
 *
 * <h2>Module boundary</h2>
 * Part of the {@code core} application module (like {@code core.auth}, {@code core.security}); exposed as
 * the {@code audit} {@link org.springframework.modulith.NamedInterface} so feature modules (e.g.
 * {@code practices}) can record disclosures without a cycle. The audit module knows nothing about
 * SCM users beyond a scalar id — joining an actor id back to a login/name is the caller's concern.
 */
@org.springframework.modulith.NamedInterface("audit")
package de.tum.cit.aet.hephaestus.core.audit;

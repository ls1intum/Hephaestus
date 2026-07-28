/**
 * Data-access disclosure trail — an append-only record of every time a privileged actor was served
 * another person's data.
 *
 * <p>The first (and today only) producer is the practice-report surface: a mentor opening a named
 * developer's report, or listing the roster that names developers. The row is deliberately
 * resource-typed ({@code DataAccessResourceType}) so a future disclosure surface reuses this table
 * instead of growing a bespoke one.
 *
 * <h2>Why a peer of {@code config_audit_event} and {@code auth_event}, not a reuse</h2>
 * Three trails, three questions. {@code auth_event} is authentication forensics keyed on the login
 * {@code Account}; {@code config_audit_event} is configuration <em>changes</em> with before/after snapshots;
 * this is data <em>reads</em> keyed on the SCM actor, which a person can occupy without ever signing in.
 *
 * <h2>The subject reads it; nobody else does</h2>
 * There is exactly one read path, and it belongs to the person the rows are about: the GDPR data export
 * includes their own disclosures ({@code core.auth.export}), answering Art. 15(1)(c) — "the recipients … to
 * whom the personal data have been disclosed" — without an operator in the loop. The index
 * {@code ix_data_access_event_subject} is built for that query.
 *
 * <p>No administrator-facing read exists: the audience for a disclosure record is the person disclosed, plus
 * an operator with database access. Adding a "what has this admin been reading" view would invert the point
 * of the table.
 *
 * <p>Retention is 365 days ({@code DataAccessRetentionJob}), matching the other two trails.
 *
 * <h2>Immutability</h2>
 * A {@code prod}-context trigger blocks UPDATE, DELETE and TRUNCATE with three carve-outs: erasure may NULL
 * the actor/subject references (per column), retention may DELETE past the window, and the workspace purge
 * may DELETE inside it under a transaction-local marker. See {@code DataAccessAuditRecorder} and the
 * changelog for the reasoning behind each.
 */
package de.tum.cit.aet.hephaestus.core.audit.access;

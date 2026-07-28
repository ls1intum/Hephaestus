/**
 * Cross-module ports for the audit trails {@code core.audit} owns.
 *
 * <p>{@code core.audit} owns the append-only {@code config_audit_event} and {@code data_access_event}
 * tables, but the modules that mutate admin configuration ({@code agent}, {@code workspace},
 * {@code practices}) or disclose someone's data ({@code practices}) must not reach into its internals to
 * write a row. These ports invert that, mirroring {@code core.auth.spi}: {@code core.audit} implements
 * {@link de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort} (configuration <em>changes</em>) and
 * {@link de.tum.cit.aet.hephaestus.core.audit.spi.DataAccessAuditPort} (data <em>disclosures</em>), and the
 * owning modules consume them.
 *
 * <p>Only primitives, enums, and the {@link de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot}
 * marker cross this boundary — never a domain entity. Producers build their own snapshot records, which
 * is what keeps redaction at the source (see {@code ConfigAuditSnapshotArchTest}).
 */
@org.springframework.modulith.NamedInterface("audit-spi")
package de.tum.cit.aet.hephaestus.core.audit.spi;

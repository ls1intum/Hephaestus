/**
 * Cross-module port for the admin configuration audit trail.
 *
 * <p>{@code core.audit} owns the append-only {@code config_audit_event} table, but the modules that
 * mutate admin configuration must not reach into its internals to write a row. This port inverts that,
 * mirroring {@code core.auth.spi}:
 * {@code core.audit} implements {@link de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditPort} and
 * the config owners consume it.
 *
 * <p>Only primitives, enums, and the {@link de.tum.cit.aet.hephaestus.core.audit.spi.ConfigAuditSnapshot}
 * marker cross this boundary — never a domain entity. Producers build their own snapshot records, which
 * is what keeps redaction at the source (see {@code ConfigAuditSnapshotArchTest}).
 */
@org.springframework.modulith.NamedInterface("config-audit-spi")
@org.jspecify.annotations.NullMarked
package de.tum.cit.aet.hephaestus.core.audit.spi;

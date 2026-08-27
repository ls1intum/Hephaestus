package de.tum.cit.aet.hephaestus.core.audit.spi;

/**
 * Marker for a producer-authored snapshot of an admin-configurable resource, serialized into
 * {@code config_audit_event.old_value} / {@code new_value}.
 *
 * <p>Implementations MUST be records: Jackson serializes record components in declaration order, and
 * both the change diff and no-op suppression depend on that determinism. A {@code Map} would not be
 * deterministic ({@code HashMap} iteration order is not contractual).
 *
 * <p>Implementations MUST NOT expose credential or contact material. The audit table is append-only,
 * so a leaked secret cannot be edited out. Snapshot a {@code boolean} presence flag instead (e.g.
 * {@code llmApiKeySet}). {@code ConfigAuditSnapshotArchTest} fails the build on a component whose name
 * looks like a secret.
 *
 * <p>Not {@code sealed}: with no {@code module-info.java}, the JLS would force every implementation
 * into this package, dragging producer domain shapes into {@code core}.
 */
public interface ConfigAuditSnapshot {}

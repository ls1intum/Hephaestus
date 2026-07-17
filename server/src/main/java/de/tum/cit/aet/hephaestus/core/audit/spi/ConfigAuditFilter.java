package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Optional constraints on an audit query; a null field means "no constraint on that dimension".
 * The workspace is deliberately <em>not</em> here — it is the tenancy boundary on the workspace-scoped
 * read, not a filter, so it cannot be collapsed to "all" by omitting it.
 *
 * @param entityId   only meaningful together with {@code entityType}; id spaces are per-type and would
 *                   otherwise collide
 * @param changedKey a dot-path from {@code changedKeys} — the per-control history filter (#1357)
 * @param to         exclusive upper bound
 */
public record ConfigAuditFilter(
    @Nullable ConfigAuditEntityType entityType,
    @Nullable String entityId,
    @Nullable String changedKey,
    @Nullable ConfigAuditAction action,
    @Nullable Long actorId,
    @Nullable Instant from,
    @Nullable Instant to
) {
    /** Enum name for SQL binding, or null when the dimension is unconstrained. */
    public @Nullable String entityTypeName() {
        return entityType == null ? null : entityType.name();
    }

    public @Nullable String actionName() {
        return action == null ? null : action.name();
    }
}

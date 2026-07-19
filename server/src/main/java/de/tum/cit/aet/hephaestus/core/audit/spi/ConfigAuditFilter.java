package de.tum.cit.aet.hephaestus.core.audit.spi;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Optional constraints on an audit query; a null/empty field means "no constraint on that dimension".
 * The workspace is deliberately not here — it is the tenancy boundary on the workspace-scoped read, not
 * a filter, so it cannot be collapsed to "all" by omitting it.
 *
 * <p>{@code entityTypes} and {@code actions} are multi-valued because audit triage is union-shaped
 * ("everything destructive" = CREATED ∪ DELETED). They reach SQL as a comma-joined string that
 * {@code string_to_array} expands — enum names contain no commas, and this keeps the optional-filter
 * shape identical to the scalar dimensions.
 */
public record ConfigAuditFilter(
    @Nullable List<ConfigAuditEntityType> entityTypes,
    @Nullable String entityId,
    @Nullable String changedKey,
    @Nullable List<ConfigAuditAction> actions,
    @Nullable Long actorId,
    @Nullable Instant from,
    @Nullable Instant to
) {
    /** Comma-joined entity-type names for SQL binding, or null when unconstrained. */
    public @Nullable String entityTypesCsv() {
        return csv(entityTypes);
    }

    /** Comma-joined action names for SQL binding, or null when unconstrained. */
    public @Nullable String actionsCsv() {
        return csv(actions);
    }

    private static @Nullable String csv(@Nullable List<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values
            .stream()
            .map(Enum::name)
            .reduce((a, b) -> a + "," + b)
            .orElse(null);
    }
}

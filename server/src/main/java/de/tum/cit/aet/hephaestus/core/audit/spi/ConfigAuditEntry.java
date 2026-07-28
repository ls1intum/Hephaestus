package de.tum.cit.aet.hephaestus.core.audit.spi;

import org.jspecify.annotations.Nullable;

/**
 * What a producer hands {@link ConfigAuditPort}. A parameter object rather than a fluent builder:
 * the transactional guarantee lives on the port's proxied bean method, and a builder's terminal call
 * would run on a plain object that no Spring proxy advises.
 *
 * @param entityType the kind of resource that changed
 * @param entityId   the resource's <em>immutable</em> key as text — the numeric primary key, never a
 *                   slug. Slugs are mutable (hence {@code workspace_slug_history}), and re-keying on
 *                   rename would silently split a resource's history in two, stranding the older half.
 *                   The slug belongs inside the snapshot, where a rename shows up as a normal diff.
 * @param workspaceId the owning workspace
 * @param before     state prior to the change; {@code null} for {@link ConfigAuditAction#CREATED}
 * @param after      state after the change; {@code null} for {@link ConfigAuditAction#DELETED}
 */
public record ConfigAuditEntry(
    ConfigAuditEntityType entityType,
    String entityId,
    Long workspaceId,
    @Nullable ConfigAuditSnapshot before,
    @Nullable ConfigAuditSnapshot after
) {
    public ConfigAuditEntry {
        if (before == null && after == null) {
            throw new IllegalArgumentException("config audit entry needs at least one of before/after");
        }
    }

    public static ConfigAuditEntry created(
        ConfigAuditEntityType entityType,
        Object entityId,
        Long workspaceId,
        ConfigAuditSnapshot after
    ) {
        return new ConfigAuditEntry(
            entityType,
            String.valueOf(entityId),
            workspaceId,
            null,
            requireSnapshot(after, "after")
        );
    }

    public static ConfigAuditEntry updated(
        ConfigAuditEntityType entityType,
        Object entityId,
        Long workspaceId,
        ConfigAuditSnapshot before,
        ConfigAuditSnapshot after
    ) {
        // Both sides are required: jspecify nullness is not enforced at runtime, so a producer that
        // captured `before` after the mutation (or not at all) would otherwise silently downgrade an
        // UPDATE to a CREATED row with no prior state — and skip no-op suppression while doing it.
        return new ConfigAuditEntry(
            entityType,
            String.valueOf(entityId),
            workspaceId,
            requireSnapshot(before, "before"),
            requireSnapshot(after, "after")
        );
    }

    public static ConfigAuditEntry deleted(
        ConfigAuditEntityType entityType,
        Object entityId,
        Long workspaceId,
        ConfigAuditSnapshot before
    ) {
        return new ConfigAuditEntry(
            entityType,
            String.valueOf(entityId),
            workspaceId,
            requireSnapshot(before, "before"),
            null
        );
    }

    private static ConfigAuditSnapshot requireSnapshot(@Nullable ConfigAuditSnapshot snapshot, String side) {
        if (snapshot == null) {
            throw new IllegalArgumentException("config audit entry is missing its '" + side + "' snapshot");
        }
        return snapshot;
    }

    public ConfigAuditAction action() {
        if (before == null) {
            return ConfigAuditAction.CREATED;
        }
        return after == null ? ConfigAuditAction.DELETED : ConfigAuditAction.UPDATED;
    }
}

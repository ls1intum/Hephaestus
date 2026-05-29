package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * The repository-scoped entity whose pagination cursor a {@link BackfillStateProvider}
 * persists, keyed by {@code syncTargetId}.
 *
 * <p>Collapses what used to be three near-identical {@code update<X>SyncCursor} methods
 * into one {@link BackfillStateProvider#updateSyncCursor} call. The implementer switches on
 * the kind to reach the correct persisted column — impl-internal column routing, not vendor
 * branching.
 */
public enum SyncCursorKind {
    /** Issue sync cursor. */
    ISSUE,
    /** Pull request / merge request sync cursor. */
    PULL_REQUEST,
    /** Discussion sync cursor. */
    DISCUSSION,
}

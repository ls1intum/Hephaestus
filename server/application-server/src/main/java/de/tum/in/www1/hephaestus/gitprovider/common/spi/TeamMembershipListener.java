package de.tum.in.www1.hephaestus.gitprovider.common.spi;

/**
 * Listener for team membership sync events.
 * <p>
 * gitprovider defines this interface and calls it after a team sync completes.
 * Consuming modules implement this to reconcile workspace-level state with
 * the synced team membership graph (e.g. ensure that every contributor who
 * appears in a team under the workspace is also listed as a workspace member).
 * <p>
 * This follows the Dependency Inversion Principle: gitprovider depends on the
 * abstraction it defines, not on consuming module concepts.
 */
public interface TeamMembershipListener {
    /**
     * Called when team memberships for a root group/organization have been fully
     * synced via scheduled sync.
     * <p>
     * Implementations should reconcile downstream state (e.g., workspace memberships)
     * with the synced team membership graph. Only fired when the team sync
     * completed normally end-to-end — never on partial data.
     *
     * @param event the team sync completed event data
     */
    default void onTeamMembershipsSynced(TeamsSyncedEvent event) {}

    /**
     * Event data for team sync completion.
     *
     * @param scopeId           the workspace/scope ID under which teams were synced
     * @param rootGroupFullPath the root group full path whose descendants were synced
     *                          (stored as {@code Team.organization} for every team
     *                          created under this root)
     */
    record TeamsSyncedEvent(Long scopeId, String rootGroupFullPath) {}
}

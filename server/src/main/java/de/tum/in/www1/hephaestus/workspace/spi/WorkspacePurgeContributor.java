package de.tum.in.www1.hephaestus.workspace.spi;

/**
 * SPI for modules that need to clean up data when a workspace is purged.
 *
 * <p>Implementations are called during workspace purge to delete module-specific
 * workspace-scoped data. This allows modules like {@code activity} to handle their
 * own cleanup without creating circular dependencies with the workspace module.
 *
 * <p>The purge operation runs within a single transaction, so implementations
 * should perform efficient bulk deletes rather than entity-by-entity deletion.
 *
 * @see de.tum.in.www1.hephaestus.workspace.WorkspaceLifecycleService#purgeWorkspace
 */
public interface WorkspacePurgeContributor {
    /**
     * Delete all data associated with the given workspace.
     *
     * <p>Called during workspace purge within the same transaction.
     * Implementations should use bulk delete queries for efficiency.
     *
     * @param workspaceId the ID of the workspace being purged
     */
    void deleteWorkspaceData(Long workspaceId);

    /**
     * Returns the order in which this contributor should be invoked.
     * Lower values are executed first. Default is 0.
     *
     * <p>Use this to ensure correct deletion order when there are
     * foreign key constraints between modules.
     *
     * @return the execution order (lower = earlier)
     */
    default int getOrder() {
        return 0;
    }
}

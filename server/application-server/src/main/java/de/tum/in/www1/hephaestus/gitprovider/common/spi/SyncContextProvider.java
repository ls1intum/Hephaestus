package de.tum.in.www1.hephaestus.gitprovider.common.spi;

/**
 * Manages thread-local sync context for workspace isolation and logging.
 */
public interface SyncContextProvider {
    void setContext(SyncContext context);

    void clearContext();

    Runnable wrapWithContext(Runnable runnable);

    record SyncContext(Long workspaceId, String workspaceSlug, String displayName, Long installationId) {}
}

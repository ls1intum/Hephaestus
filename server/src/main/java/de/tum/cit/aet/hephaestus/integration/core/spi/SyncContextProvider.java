package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Manages thread-local sync context for scope isolation and logging.
 */
public interface SyncContextProvider {
    void setContext(SyncContext context);

    void clearContext();

    Runnable wrapWithContext(Runnable runnable);

    record SyncContext(Long scopeId, String slug, String displayName, Long installationId) {}
}

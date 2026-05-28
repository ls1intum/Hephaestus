package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Runs vendor-specific async initialization for a freshly-created workspace.
 *
 * <p>Called by {@code WorkspaceService.createWorkspaceWithInitialization} once the new
 * workspace row has committed. Implementations dispatch to vendor-specific bootstrap flows
 * (GitLab: group discovery + webhook registration; future kinds: their own equivalents).
 * Fire-and-forget by contract — implementations must dispatch async and never throw on
 * failure (the workspace creation HTTP response has already been sent by the time the hook
 * runs).
 *
 * <p>Symmetric with {@link WorkspaceProvisioningHook}, which handles the inverse flow
 * (external installations → workspaces).
 */
public interface WorkspaceInitializationHook {
    /** The integration kind this hook initializes for. */
    IntegrationKind kind();

    /** Async-fires vendor-specific initialization for the freshly-created workspace. */
    void initializeAsync(long workspaceId);
}

package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Reconciles externally-owned identity bindings into the workspace registry at startup.
 *
 * <p>Hooks are invoked by {@code WorkspaceStartupListener} once per application boot,
 * before workspace activation begins. The GitHub App adapter implements this to walk the
 * App's installation list and ensure every installation has a matching workspace; future
 * adapters (Bitbucket OAuth apps, GitLab system hooks, …) can plug in symmetrically.
 *
 * <p>Per-hook failures are caught by the caller — implementations may throw, and the
 * remaining hooks still run. Implementations are expected to be idempotent: the startup
 * listener may invoke them again on the next boot, and rolling restarts in production must
 * not duplicate workspaces.
 */
public interface WorkspaceProvisioningHook {
    /** The integration kind whose external system is being reconciled. */
    IntegrationKind kind();

    /**
     * Runs the reconciliation. Implementations should log per-installation outcomes
     * (created / updated / suspended / skipped) and swallow per-installation failures so
     * a single bad installation never aborts the whole walk.
     */
    void reconcile();
}

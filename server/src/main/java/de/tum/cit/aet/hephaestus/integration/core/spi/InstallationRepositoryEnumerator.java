package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.util.List;

/**
 * Enumerates the repositories an external installation has access to.
 *
 * <p>The workspace module uses this port (instead of importing
 * {@code GitHubInstallationRepositoryEnumerationService}) so that {@code RepositoryToMonitor}
 * reconciliation against the App's repo list stays vendor-agnostic. One impl per kind that
 * supports installation-bound bindings; kinds without that concept (GitLab PAT today)
 * simply don't register an impl.
 */
public interface InstallationRepositoryEnumerator {
    /** The integration kind this enumerator covers. */
    IntegrationKind kind();

    /**
     * Enumerates the repositories visible to the given installation. Implementations may
     * return an empty list on a transient API failure — callers must NOT treat empty as
     * "removed everything" without an independent signal.
     */
    List<InstallationRepository> enumerate(long installationId);

    /**
     * Lightweight repository projection. Mirrors the {@code RepositorySnapshot} pattern
     * used elsewhere in the framework so the workspace module never imports the vendor's
     * snapshot type.
     */
    record InstallationRepository(long id, String nameWithOwner, String name, boolean isPrivate) {}
}

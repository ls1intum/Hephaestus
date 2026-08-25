package de.tum.cit.aet.hephaestus.integration.core.spi;

/**
 * Tracks whether a GitHub App installation (or analogous external-app binding) is
 * marked suspended in-memory.
 *
 * <p>Suspension marking is owned by the vendor adapter that interacts with the App's
 * suspend/unsuspend lifecycle. The workspace module consults this port to short-circuit
 * repository monitor changes for installations that the App has marked suspended — those
 * changes would dispatch syncs that the App is no longer permitted to make.
 *
 * <p>"Installation" is GitHub-shaped today but the SPI is named neutrally because
 * other providers (Bitbucket OAuth apps, future SCMs) may grow analogous bindings.
 * Implementations gate on {@link #kind()}; callers dispatch by the workspace's bound kind.
 */
public interface InstallationSuspensionTracker {
    /** The integration kind this tracker covers. */
    IntegrationKind kind();

    /** Whether the given installation id has been marked suspended in-memory. */
    boolean isInstallationMarkedSuspended(long installationId);
}

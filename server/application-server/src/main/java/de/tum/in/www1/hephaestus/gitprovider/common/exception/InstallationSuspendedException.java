package de.tum.in.www1.hephaestus.gitprovider.common.exception;

import java.io.Serial;

/**
 * Thrown when attempting to use a suspended GitHub App installation.
 * This is a non-retryable error - the installation must be unsuspended before any API calls can succeed.
 */
public class InstallationSuspendedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final long installationId;

    public InstallationSuspendedException(long installationId) {
        super(formatMessage(installationId));
        this.installationId = installationId;
    }

    public InstallationSuspendedException(long installationId, Throwable cause) {
        super(formatMessage(installationId), cause);
        this.installationId = installationId;
    }

    public long getInstallationId() {
        return installationId;
    }

    private static String formatMessage(long installationId) {
        return "Installation " + installationId + " is suspended. Refusing to mint token.";
    }
}

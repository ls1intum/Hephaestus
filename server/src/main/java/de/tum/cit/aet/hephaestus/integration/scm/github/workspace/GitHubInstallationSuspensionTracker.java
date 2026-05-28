package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationSuspensionTracker;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.github.app.GitHubAppTokenService;
import org.springframework.stereotype.Component;

/**
 * Wraps {@link GitHubAppTokenService#isInstallationMarkedSuspended} as the GitHub-side
 * {@link InstallationSuspensionTracker} impl. Identity wrapper — the underlying state is
 * still owned by the App token service, this just keeps the workspace module free of the
 * direct dependency.
 */
@Component
public class GitHubInstallationSuspensionTracker implements InstallationSuspensionTracker {

    private final GitHubAppTokenService gitHubAppTokenService;

    public GitHubInstallationSuspensionTracker(GitHubAppTokenService gitHubAppTokenService) {
        this.gitHubAppTokenService = gitHubAppTokenService;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public boolean isInstallationMarkedSuspended(long installationId) {
        return gitHubAppTokenService.isInstallationMarkedSuspended(installationId);
    }
}

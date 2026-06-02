package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.WorkspaceProviderAvailability;
import de.tum.cit.aet.hephaestus.integration.scm.github.GitHubProperties;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Exposes the GitHub-App installation URL to the workspace creation wizard.
 *
 * <p>Availability requires both {@code app.id > 0} and a configured installation URL —
 * either alone is half-configured and would yield a wizard option that breaks on click.
 */
@Component
public class GitHubWorkspaceProviderAvailability implements WorkspaceProviderAvailability {

    private final GitHubProperties gitHubProperties;

    public GitHubWorkspaceProviderAvailability(GitHubProperties gitHubProperties) {
        this.gitHubProperties = gitHubProperties;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public Optional<String> hintUrl() {
        if (gitHubProperties.app().id() > 0 && gitHubProperties.app().installationUrl() != null) {
            return Optional.of(gitHubProperties.app().installationUrl());
        }
        return Optional.empty();
    }
}

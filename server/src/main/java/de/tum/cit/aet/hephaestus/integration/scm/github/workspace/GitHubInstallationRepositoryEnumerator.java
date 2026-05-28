package de.tum.cit.aet.hephaestus.integration.scm.github.workspace;

import de.tum.cit.aet.hephaestus.integration.core.spi.InstallationRepositoryEnumerator;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.github.installation.GitHubInstallationRepositoryEnumerationService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Adapter from {@link GitHubInstallationRepositoryEnumerationService} (which returns
 * {@code InstallationRepositorySnapshot}, a github-package type) to the kind-agnostic
 * {@link InstallationRepositoryEnumerator} SPI consumed by the workspace module.
 */
@Component
public class GitHubInstallationRepositoryEnumerator implements InstallationRepositoryEnumerator {

    private final GitHubInstallationRepositoryEnumerationService delegate;

    public GitHubInstallationRepositoryEnumerator(GitHubInstallationRepositoryEnumerationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public IntegrationKind kind() {
        return IntegrationKind.GITHUB;
    }

    @Override
    public List<InstallationRepository> enumerate(long installationId) {
        return delegate
            .enumerate(installationId)
            .stream()
            .map(s -> new InstallationRepository(s.id(), s.nameWithOwner(), s.name(), s.isPrivate()))
            .toList();
    }
}

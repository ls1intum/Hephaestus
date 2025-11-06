package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLink;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLinkRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Installation Repositories Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubInstallationRepositoriesMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationRepositoriesMessageHandler handler;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private InstallationRepository installationRepository;

    @Autowired
    private InstallationRepositoryLinkRepository linkRepository;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should link repositories on installation_repositories.added")
    void shouldLinkRepositoriesOnAdded(
        @GitHubPayload("installation_repositories.added") GHEventPayload.InstallationRepositories payload
    ) {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(payload.getInstallation().getId());
        workspace.setAccountLogin(payload.getInstallation().getAccount().getLogin());
        workspaceRepository.save(workspace);

        handler.handleEvent(payload);

        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));

        assertThat(installation.getRepositorySelection()).isEqualTo(Installation.RepositorySelection.ALL);

        var addedRepo = payload.getRepositoriesAdded().getFirst();
        assertThat(repositoryRepository.findByNameWithOwner(addedRepo.getFullName())).isPresent();

        var links = linkRepository.findAllByIdInstallationId(installation.getId());
        assertThat(links)
            .hasSize(1)
            .first()
            .satisfies(link -> {
                assertThat(link.isActive()).isTrue();
                assertThat(link.getRepository().getId()).isEqualTo(addedRepo.getId());
            });

        var refreshedWorkspace = workspaceRepository
            .findByInstallationId(installation.getId())
            .orElseThrow(() -> new AssertionError("Workspace missing"));
        var activeMonitors = refreshedWorkspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(RepositoryToMonitor::isActive)
            .map(RepositoryToMonitor::getNameWithOwner)
            .toList();
        assertThat(activeMonitors).containsExactly(addedRepo.getFullName());
    }

    @Test
    @DisplayName("Should deactivate repositories on installation_repositories.removed")
    void shouldDeactivateRepositoriesOnRemoved(
        @GitHubPayload("installation_repositories.added") GHEventPayload.InstallationRepositories addedPayload,
        @GitHubPayload("installation_repositories.removed") GHEventPayload.InstallationRepositories removedPayload
    ) {
        handler.handleEvent(addedPayload);
        var installation = installationRepository
            .findById(addedPayload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));

        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(installation.getId());
        workspace.setAccountLogin(addedPayload.getInstallation().getAccount().getLogin());
        workspaceRepository.save(workspace);

        removedPayload
            .getRepositoriesRemoved()
            .forEach(repoPayload -> {
                var repository = repositorySyncService.upsertFromInstallationPayload(
                    repoPayload.getId(),
                    repoPayload.getFullName(),
                    repoPayload.getName(),
                    repoPayload.isPrivate()
                );
                var link = new InstallationRepositoryLink();
                link.setId(new InstallationRepositoryLink.Id(installation.getId(), repository.getId()));
                link.setInstallation(installation);
                link.setRepository(repository);
                link.setActive(true);
                link.setLinkedAt(Instant.now());
                link.setLastSyncedAt(Instant.now());
                linkRepository.save(link);

                RepositoryToMonitor repoToMonitor = new RepositoryToMonitor();
                repoToMonitor.setNameWithOwner(repoPayload.getFullName());
                repoToMonitor.setWorkspace(workspace);
                repoToMonitor.setSource(RepositoryToMonitor.Source.INSTALLATION);
                repoToMonitor.setRepository(repository);
                repoToMonitor.setLinkedAt(Instant.now());
                repoToMonitor.setActive(true);
                repoToMonitor.setInstallationId(installation.getId());
                repositoryToMonitorRepository.save(repoToMonitor);
                workspace.getRepositoriesToMonitor().add(repoToMonitor);
            });
        workspaceRepository.save(workspace);

        handler.handleEvent(removedPayload);

        var removedIds = removedPayload
            .getRepositoriesRemoved()
            .stream()
            .map(repo -> repo.getId())
            .collect(java.util.stream.Collectors.toSet());

        linkRepository
            .findAllByIdInstallationId(installation.getId())
            .stream()
            .filter(link -> removedIds.contains(link.getRepository().getId()))
            .forEach(link -> {
                assertThat(link.isActive()).isFalse();
                assertThat(link.getRemovedAt()).isNotNull();
            });
        var refreshedInstallation = installationRepository
            .findById(installation.getId())
            .orElseThrow(() -> new AssertionError("Installation missing after repositories removal"));

        assertThat(refreshedInstallation.getRepositorySelection()).isEqualTo(Installation.RepositorySelection.SELECTED);

        var refreshedWorkspace = workspaceRepository
            .findByInstallationId(installation.getId())
            .orElseThrow(() -> new AssertionError("Workspace missing"));
        var expectedRepositories = linkRepository
            .findAllByIdInstallationId(installation.getId())
            .stream()
            .filter(InstallationRepositoryLink::isActive)
            .map(link -> link.getId().getRepositoryId())
            .map(repoId ->
                repositoryRepository
                    .findById(repoId)
                    .orElseThrow(() -> new AssertionError("Repository missing: " + repoId))
                    .getNameWithOwner()
            )
            .toList();
        var activeWorkspaceRepositories = refreshedWorkspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(RepositoryToMonitor::isActive)
            .map(RepositoryToMonitor::getNameWithOwner)
            .toList();
        assertThat(activeWorkspaceRepositories).containsExactlyInAnyOrderElementsOf(expectedRepositories);
    }
}

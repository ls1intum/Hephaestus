package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Workspace Service repository orchestration")
class WorkspaceServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @BeforeEach
    void clean() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @Transactional
    @DisplayName("deactivating an installation repository keeps canonical repository data")
    void shouldDeactivateMonitorWithoutDeletingRepository() {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setAccountLogin("hephaestus");
        workspace.setInstallationId(4242L);
        workspace = workspaceRepository.save(workspace);

        Repository repository = new Repository();
        repository.setId(1717L);
        repository.setCreatedAt(Instant.now());
        repository.setUpdatedAt(Instant.now());
        repository.setName("core");
        repository.setNameWithOwner("hephaestus/core");
        repository.setHtmlUrl("https://github.com/hephaestus/core");
        repository.setPrivate(true);
        repository.setPushedAt(Instant.now());
        repository.setVisibility(Repository.Visibility.PRIVATE);
        repository.setDefaultBranch("main");
        repositoryRepository.save(repository);

        boolean activated = workspaceService.registerInstallationRepositorySnapshot(
            workspace,
            repository.getId(),
            repository.getNameWithOwner(),
            repository.getName(),
            repository.isPrivate()
        );
        assertThat(activated).isTrue();

        workspaceService.syncInstallationRepositoryLinks(workspace.getInstallationId(), Set.of());

        Workspace reloadedWorkspace = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(reloadedWorkspace.getRepositoriesToMonitor()).hasSize(1);

        RepositoryToMonitor monitor = reloadedWorkspace.getRepositoriesToMonitor().iterator().next();
        assertThat(monitor.isActive()).isFalse();
        assertThat(monitor.getNameWithOwner()).isEqualTo("hephaestus/core");
        assertThat(monitor.getUnlinkedAt()).isNotNull();

        assertThat(repositoryRepository.findById(repository.getId())).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("installation workspaces reject manual repository mutations")
    void shouldRejectManualChangesForInstallationWorkspace() {
        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setAccountLogin("hephaestus");
        workspace.setInstallationId(5150L);
        workspaceRepository.save(workspace);

        Throwable addException = catchThrowable(() -> workspaceService.addRepositoryToMonitor("hephaestus/api"));
        assertThat(addException)
            .isInstanceOf(WorkspaceRepositoryMutationNotAllowedException.class)
            .hasMessageContaining("Manual repository management is disabled");

        Throwable removeException = catchThrowable(() -> workspaceService.removeRepositoryToMonitor("hephaestus/api"));
        assertThat(removeException)
            .isInstanceOf(WorkspaceRepositoryMutationNotAllowedException.class)
            .hasMessageContaining("Manual repository management is disabled");
    }
}

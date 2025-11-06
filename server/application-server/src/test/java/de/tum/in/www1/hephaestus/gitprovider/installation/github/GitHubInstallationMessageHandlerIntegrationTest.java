package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.installation.Installation;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepository;
import de.tum.in.www1.hephaestus.gitprovider.installation.InstallationRepositoryLinkRepository;
import de.tum.in.www1.hephaestus.gitprovider.installationtarget.InstallationTargetRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("GitHub Installation Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Transactional
class GitHubInstallationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationMessageHandler handler;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private InstallationRepository installationRepository;

    @Autowired
    private InstallationTargetRepository installationTargetRepository;

    @Autowired
    private InstallationRepositoryLinkRepository installationRepositoryLinkRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
    }

    @Test
    @DisplayName("Should persist installation.created payload")
    void shouldPersistInstallationCreatedPayload(
        @GitHubPayload("installation.created") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("created");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();

        List<String> fullNames = payload
            .getRawRepositories()
            .stream()
            .map(GHEventPayload.Installation.Repository::getFullName)
            .toList();

        handler.handleEvent(payload);

        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));

        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.ACTIVE);
        assertThat(installation.getRepositorySelection()).isEqualTo(Installation.RepositorySelection.ALL);
        assertThat(installation.getSubscribedEvents()).containsAll(
            payload
                .getInstallation()
                .getEvents()
                .stream()
                .map(event -> event.name().toLowerCase())
                .collect(Collectors.toSet())
        );
        assertThat(installation.getPermissions()).hasSize(payload.getInstallation().getPermissions().size());

        var target = installationTargetRepository
            .findById(payload.getInstallation().getAccount().getId())
            .orElseThrow(() -> new AssertionError("Installation target missing"));
        assertThat(target.getLogin()).isEqualTo(payload.getInstallation().getAccount().getLogin());

        var links = installationRepositoryLinkRepository.findAllByIdInstallationId(installation.getId());
        assertThat(links).hasSize(fullNames.size()).allMatch(link -> link.isActive());

        fullNames.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isPresent());
    }

    @Test
    @DisplayName("Should mark installation deleted and deactivate links")
    void shouldMarkInstallationDeleted(@GitHubPayload("installation.deleted") GHEventPayload.Installation payload) {
        assertThat(payload.getAction()).isEqualTo("deleted");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();

        handler.handleEvent(payload);

        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));
        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.DELETED);
        assertThat(installation.getDeletedAt()).isNotNull();

        var links = installationRepositoryLinkRepository.findAllByIdInstallationId(installation.getId());
        assertThat(links)
            .isNotEmpty()
            .allSatisfy(link -> {
                assertThat(link.isActive()).isFalse();
                assertThat(link.getRemovedAt()).isNotNull();
            });

        payload
            .getRawRepositories()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isPresent());
    }

    @Test
    @DisplayName("Should update installation to suspended state")
    void shouldPersistSuspendedState(@GitHubPayload("installation.suspend") GHEventPayload.Installation payload) {
        assertThat(payload.getAction()).isEqualTo("suspend");
        assertThat(payload.getInstallation()).isNotNull();

        handler.handleEvent(payload);

        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));
        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.SUSPENDED);
        assertThat(installation.getSuspendedAt()).isEqualTo(payload.getInstallation().getSuspendedAt());
        assertThat(installation.getSuspendedBy()).isNotNull();
        assertThat(installation.getSuspendedBy().getLogin()).isEqualTo(
            payload.getInstallation().getSuspendedBy().getLogin()
        );
    }

    @Test
    @DisplayName("Should restore installation to active state on unsuspend")
    void shouldClearSuspensionOnUnsuspend(
        @GitHubPayload("installation.suspend") GHEventPayload.Installation suspendPayload,
        @GitHubPayload("installation.unsuspend") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("unsuspend");
        assertThat(payload.getInstallation()).isNotNull();

        handler.handleEvent(suspendPayload);

        handler.handleEvent(payload);

        var installation = installationRepository
            .findById(payload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));
        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.ACTIVE);
        assertThat(installation.getSuspendedAt()).isNull();
        assertThat(installation.getSuspendedBy()).isNull();
    }

    @Test
    @DisplayName("Should clear deleted timestamp and resubscribe workspace repositories on reinstallation")
    void shouldReactivateInstallationAndRepositories(
        @GitHubPayload("installation.deleted") GHEventPayload.Installation deletedPayload,
        @GitHubPayload("installation.created") GHEventPayload.Installation createdPayload
    ) {
        assertThat(deletedPayload.getInstallation()).isNotNull();
        assertThat(createdPayload.getInstallation()).isNotNull();

        Workspace workspace = new Workspace();
        workspace.setGitProviderMode(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        workspace.setInstallationId(createdPayload.getInstallation().getId());
        workspace.setAccountLogin(createdPayload.getInstallation().getAccount().getLogin());
        workspaceRepository.save(workspace);

        handler.handleEvent(deletedPayload);
        handler.handleEvent(createdPayload);

        var installation = installationRepository
            .findById(createdPayload.getInstallation().getId())
            .orElseThrow(() -> new AssertionError("Installation not persisted"));

        assertThat(installation.getLifecycleState()).isEqualTo(Installation.LifecycleState.ACTIVE);
        assertThat(installation.getDeletedAt()).isNull();

        var expectedRepositories = createdPayload
            .getRawRepositories()
            .stream()
            .map(GHEventPayload.Installation.Repository::getFullName)
            .toList();

        var refreshedWorkspace = workspaceRepository
            .findByInstallationId(installation.getId())
            .orElseThrow(() -> new AssertionError("Workspace missing"));

        var activeRepositories = refreshedWorkspace
            .getRepositoriesToMonitor()
            .stream()
            .filter(RepositoryToMonitor::isActive)
            .map(RepositoryToMonitor::getNameWithOwner)
            .toList();
        assertThat(activeRepositories).containsExactlyInAnyOrderElementsOf(expectedRepositories);
    }
}

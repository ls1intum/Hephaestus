package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import de.tum.in.www1.hephaestus.workspace.WorkspaceService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayloadInstallationTarget;
import org.kohsuke.github.GHEventPayloadInstallationTarget.Account;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("GitHub Installation Target Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
class GitHubInstallationTargetMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationTargetMessageHandler handler;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private NatsConsumerService natsConsumerService;

    @Autowired
    private WorkspaceService workspaceService;

    @BeforeEach
    void clean() {
        databaseTestUtils.cleanDatabase();
        ReflectionTestUtils.setField(workspaceService, "isNatsEnabled", true);
        reset(natsConsumerService);
    }

    @AfterEach
    void afterEach() {
        reset(natsConsumerService);
    }

    @Test
    @DisplayName("Renaming installation updates workspace and monitors")
    void shouldHandleInstallationTargetRename(
        @GitHubPayload("installation_target") GHEventPayloadInstallationTarget payload
    ) {
        var installation = payload.getInstallation();
        Account account = payload.getAccount();
        long installationId = installation.getId();
        String oldLogin = payload.getChanges().getLogin().getFrom();
        String newLogin = account.getLogin();

        Workspace workspace = workspaceRepository.save(buildWorkspace(installationId, oldLogin));
        RepositoryToMonitor monitor = repositoryToMonitorRepository.save(buildMonitor(workspace, oldLogin + "/alpha"));
        repositoryRepository.save(
            repositorySyncService.upsertFromInstallationPayload(123456789L, monitor.getNameWithOwner(), "alpha", true)
        );

        handler.handleEvent(payload);

        Workspace refreshed = workspaceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(refreshed.getAccountLogin()).isEqualTo(newLogin);
        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
        assertThat(monitors).extracting(RepositoryToMonitor::getNameWithOwner).contains(newLogin + "/alpha");
        assertThat(monitors)
            .extracting(RepositoryToMonitor::getNameWithOwner)
            .noneMatch(name -> name.startsWith(oldLogin + "/"));
        assertThat(repositoryRepository.findByNameWithOwner(newLogin + "/alpha")).isPresent();
        assertThat(organizationRepository.findByInstallationId(installationId))
            .isPresent()
            .get()
            .extracting(org -> org.getLogin())
            .isEqualTo(newLogin);

        ArgumentCaptor<Workspace> workspaceCaptor = ArgumentCaptor.forClass(Workspace.class);
        // Workspace consumer is updated twice: once for repo renames, once for org rotation
        verify(natsConsumerService, atLeast(1)).updateWorkspaceConsumer(workspaceCaptor.capture());
        assertThat(workspaceCaptor.getAllValues()).isNotEmpty();
    }

    @Test
    @DisplayName("Missing change block falls back to stored workspace login")
    void shouldFallbackToWorkspaceAccountWhenChangesMissing(
        @GitHubPayload("installation_target_no_changes") GHEventPayloadInstallationTarget payload
    ) {
        long installationId = payload.getInstallation().getId();
        Workspace workspace = workspaceRepository.save(buildWorkspace(installationId, "LegacyFallback"));
        repositoryToMonitorRepository.save(buildMonitor(workspace, "LegacyFallback/bravo"));

        handler.handleEvent(payload);

        Workspace refreshed = workspaceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(refreshed.getAccountLogin()).isEqualTo(payload.getAccount().getLogin());
        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
        assertThat(monitors)
            .extracting(RepositoryToMonitor::getNameWithOwner)
            .contains(payload.getAccount().getLogin() + "/bravo");
        // Verify workspace consumer was updated for the renamed repositories
        verify(natsConsumerService, atLeast(1)).updateWorkspaceConsumer(any(Workspace.class));
    }

    @Test
    @DisplayName("User rename does not upsert organization metadata")
    void shouldSkipOrganizationUpsertForUserTargets(
        @GitHubPayload("installation_target_user") GHEventPayloadInstallationTarget payload
    ) {
        long installationId = payload.getInstallation().getId();
        Workspace workspace = workspaceRepository.save(buildWorkspace(installationId, "SoloMaintainer"));
        workspace.setAccountType(AccountType.USER);
        workspaceRepository.save(workspace);
        repositoryToMonitorRepository.save(buildMonitor(workspace, "SoloMaintainer/charlie"));

        handler.handleEvent(payload);

        Workspace updated = workspaceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(updated.getAccountLogin()).isEqualTo(payload.getAccount().getLogin());
        assertThat(organizationRepository.findByInstallationId(installationId)).isEmpty();
    }

    private Workspace buildWorkspace(long installationId, String login) {
        return WorkspaceTestFixtures.installationWorkspace(installationId, login).build();
    }

    private RepositoryToMonitor buildMonitor(Workspace workspace, String nameWithOwner) {
        return WorkspaceTestFixtures.repositoryMonitor(workspace, nameWithOwner);
    }
}

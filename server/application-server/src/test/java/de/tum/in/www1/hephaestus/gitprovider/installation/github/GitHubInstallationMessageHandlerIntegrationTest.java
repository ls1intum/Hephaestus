package de.tum.in.www1.hephaestus.gitprovider.installation.github;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayload;
import de.tum.in.www1.hephaestus.gitprovider.common.GitHubPayloadExtension;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService.InstallationRepositorySnapshot;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.repository.github.GitHubRepositorySyncService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFixtures;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitorRepository;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GHEventPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DisplayName("GitHub Installation Message Handler")
@ExtendWith(GitHubPayloadExtension.class)
@Import(GitHubInstallationMessageHandlerIntegrationTest.EnumeratorStubConfig.class)
class GitHubInstallationMessageHandlerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GitHubInstallationMessageHandler handler;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private GitHubRepositorySyncService repositorySyncService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @MockitoBean
    private NatsConsumerService natsConsumerService;

    @BeforeEach
    void setUp() {
        databaseTestUtils.cleanDatabase();
        EnumeratorStubConfig.snapshots = List.of();
    }

    @Test
    @DisplayName("Should parse installation.created payload")
    void shouldParseInstallationCreatedPayload(
        @GitHubPayload("installation.created") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("created");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();
        assertThat(payload.getRawRepositories().get(0).getFullName()).contains("/");

        long installationId = payload.getInstallation().getId();
        Workspace workspace = seedWorkspace(installationId, payload.getInstallation().getAccount().getLogin(), null);

        var extraRepo = new InstallationRepositorySnapshot(
            999_999_999L,
            payload.getInstallation().getAccount().getLogin() + "/extra-repo",
            "extra-repo",
            true
        );
        EnumeratorStubConfig.snapshots = List.of(extraRepo);

        // Precondition
        var fullNames = payload
            .getRawRepositories()
            .stream()
            .map(r -> r.getFullName())
            .toList();
        fullNames.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isEmpty());
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId())).isEmpty();
        assertThat(workspace.getGithubRepositorySelection()).isNull();

        // When
        handler.handleEvent(payload);

        // Then
        fullNames.forEach(n -> assertThat(repositoryRepository.findByNameWithOwner(n)).isPresent());
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId()))
            .extracting(r -> r.getNameWithOwner())
            .containsAll(fullNames)
            .contains(extraRepo.nameWithOwner());
        assertThat(repositoryRepository.findByNameWithOwner(extraRepo.nameWithOwner())).isPresent();
        Workspace refreshed = workspaceRepository.findByInstallationId(installationId).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        assertThat(refreshed.getGithubRepositorySelection()).isEqualTo(
            payload.getInstallation().getRepositorySelection()
        );
        // organization should be linked to installation
        assertThat(
            organizationRepository
                .findByInstallationId(installationId)
                .map(org -> org.getLogin())
                .orElse(null)
        ).isEqualTo(payload.getInstallation().getAccount().getLogin());
    }

    @Test
    @DisplayName("Should parse installation.deleted payload")
    void shouldParseInstallationDeletedPayload(
        @GitHubPayload("installation.deleted") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("deleted");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getRawRepositories()).isNotEmpty();

        long installationId = payload.getInstallation().getId();
        String accountLogin = payload.getInstallation().getAccount().getLogin();
        Workspace workspace = seedWorkspace(installationId, accountLogin, null);

        // Seed: simulate repos existing from prior installation without REST calls
        payload
            .getRawRepositories()
            .forEach(r ->
                repositorySyncService.upsertFromInstallationPayload(
                    r.getId(),
                    r.getFullName(),
                    r.getName(),
                    r.isPrivate()
                )
            );
        payload
            .getRawRepositories()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isPresent());
        payload
            .getRawRepositories()
            .forEach(r -> repositoryToMonitorRepository.save(buildMonitor(workspace, r.getFullName())));
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId())).hasSize(
            payload.getRawRepositories().size()
        );

        // Seed organization linked to installation
        var org = new de.tum.in.www1.hephaestus.gitprovider.organization.Organization();
        org.setId(payload.getInstallation().getAccount().getId());
        org.setGithubId(payload.getInstallation().getAccount().getId());
        org.setLogin(accountLogin);
        org.setInstallationId(installationId);
        organizationRepository.save(org);

        // When
        handler.handleEvent(payload);

        // Then: repositories should be removed now
        payload
            .getRawRepositories()
            .forEach(r -> assertThat(repositoryRepository.findByNameWithOwner(r.getFullName())).isEmpty());
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId())).isEmpty();
        assertThat(workspaceRepository.findByInstallationId(installationId))
            .map(Workspace::getStatus)
            .contains(Workspace.WorkspaceStatus.PURGED);
        // Organization should be detached from installation
        assertThat(organizationRepository.findByInstallationId(installationId)).isEmpty();
        assertThat(organizationRepository.findByGithubId(payload.getInstallation().getAccount().getId()))
            .isPresent()
            .get()
            .extracting(o -> o.getInstallationId())
            .isNull();
    }

    @Test
    @DisplayName("Deleted installation removes monitors AND repositories even when GitHub omits repository list")
    void shouldRemoveMonitorsAndRepositoriesWhenDeletedPayloadHasNoRepositories(
        @GitHubPayload("installation.deleted-empty") GHEventPayload.Installation payload
    ) {
        long installationId = payload.getInstallation().getId();
        String accountLogin = payload.getInstallation().getAccount().getLogin();
        Workspace workspace = seedWorkspace(installationId, accountLogin, null);

        // Seed ACTUAL Repository entities (not just monitors) - simulating repos from installation
        repositorySyncService.upsertFromInstallationPayload(1001L, accountLogin + "/one", "one", false);
        repositorySyncService.upsertFromInstallationPayload(1002L, accountLogin + "/two", "two", true);
        repositorySyncService.upsertFromInstallationPayload(1003L, accountLogin + "/three", "three", false);

        // Seed monitors for these repos
        repositoryToMonitorRepository.save(buildMonitor(workspace, accountLogin + "/one"));
        repositoryToMonitorRepository.save(buildMonitor(workspace, accountLogin + "/two"));
        repositoryToMonitorRepository.save(buildMonitor(workspace, accountLogin + "/three"));

        // Verify preconditions
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/one")).isPresent();
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/two")).isPresent();
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/three")).isPresent();
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId())).hasSize(3);

        // The payload has empty repositories array - simulates GitHub not sending repo list
        assertThat(payload.getRawRepositories()).isEmpty();

        // When
        handler.handleEvent(payload);

        // Then: BOTH monitors AND repositories should be cleaned up via owner prefix
        assertThat(repositoryToMonitorRepository.findByWorkspaceId(workspace.getId())).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/one")).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/two")).isEmpty();
        assertThat(repositoryRepository.findByNameWithOwner(accountLogin + "/three")).isEmpty();
        assertThat(workspaceRepository.findByInstallationId(installationId))
            .map(Workspace::getStatus)
            .contains(Workspace.WorkspaceStatus.PURGED);
    }

    @Test
    @DisplayName("Deleted installation event does not create a workspace when none exists")
    void shouldIgnoreDeletionForUnknownWorkspace(
        @GitHubPayload("installation.deleted") GHEventPayload.Installation payload
    ) {
        long installationId = payload.getInstallation().getId();

        handler.handleEvent(payload);

        assertThat(workspaceRepository.findByInstallationId(installationId)).isEmpty();
    }

    @Test
    @DisplayName("Should parse installation.suspend payload")
    void shouldParseInstallationSuspendPayload(
        @GitHubPayload("installation.suspend") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("suspend");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getSender()).isNotNull();

        seedWorkspace(payload.getInstallation().getId(), payload.getInstallation().getAccount().getLogin(), null);
        handler.handleEvent(payload);

        assertThat(workspaceRepository.findByInstallationId(payload.getInstallation().getId()))
            .map(Workspace::getStatus)
            .contains(Workspace.WorkspaceStatus.SUSPENDED);
    }

    @Test
    @DisplayName("Should parse installation.unsuspend payload")
    void shouldParseInstallationUnsuspendPayload(
        @GitHubPayload("installation.unsuspend") GHEventPayload.Installation payload
    ) {
        assertThat(payload.getAction()).isEqualTo("unsuspend");
        assertThat(payload.getInstallation()).isNotNull();
        assertThat(payload.getSender()).isNotNull();

        seedWorkspace(
            payload.getInstallation().getId(),
            payload.getInstallation().getAccount().getLogin(),
            Workspace.WorkspaceStatus.SUSPENDED
        );
        handler.handleEvent(payload);

        assertThat(workspaceRepository.findByInstallationId(payload.getInstallation().getId()))
            .map(Workspace::getStatus)
            .contains(Workspace.WorkspaceStatus.ACTIVE);
    }

    private Workspace seedWorkspace(long installationId, String login, Workspace.WorkspaceStatus status) {
        return workspaceRepository.save(
            WorkspaceTestFixtures.installationWorkspace(installationId, login)
                .withStatus(status != null ? status : Workspace.WorkspaceStatus.ACTIVE)
                .withSlug("ws-" + installationId)
                .build()
        );
    }

    private de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor buildMonitor(
        Workspace workspace,
        String nameWithOwner
    ) {
        return WorkspaceTestFixtures.repositoryMonitor(workspace, nameWithOwner);
    }

    @TestConfiguration
    static class EnumeratorStubConfig {

        static volatile List<InstallationRepositorySnapshot> snapshots = List.of();

        @Bean
        @Primary
        GitHubInstallationRepositoryEnumerationService gitHubInstallationRepositoryEnumerator(
            GitHubAppTokenService tokens
        ) {
            return new GitHubInstallationRepositoryEnumerationService(tokens) {
                @Override
                public List<InstallationRepositorySnapshot> enumerate(long installationId) {
                    return snapshots;
                }
            };
        }
    }
}

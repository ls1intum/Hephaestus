package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderType;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.installation.github.GitHubInstallationRepositoryEnumerationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseIntegrationTest;
import de.tum.in.www1.hephaestus.testconfig.WorkspaceTestFixtures;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

class WorkspaceRepositoryCoverageIntegrationTest extends BaseIntegrationTest {

    private static final long INSTALLATION_ID = 9912345L;

    @Autowired
    private WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @MockitoBean
    private GitHubInstallationRepositoryEnumerationService repositoryEnumerator;

    @Autowired
    private GitHubAppTokenService gitHubAppTokenService;

    @Autowired
    private GitProviderRepository gitProviderRepository;

    @BeforeEach
    void setup() {
        databaseTestUtils.cleanDatabase();
        // Ensure GitHub GitProvider exists - required by WorkspaceRepositoryMonitorService.ensureRepositoryFromSnapshot
        gitProviderRepository
            .findByTypeAndServerUrl(GitProviderType.GITHUB, "https://github.com")
            .orElseGet(() -> gitProviderRepository.save(new GitProvider(GitProviderType.GITHUB, "https://github.com")));
        // Clear any suspended installation state from previous tests
        // This prevents test pollution when async syncs mark installations as suspended
        gitHubAppTokenService.markInstallationActive(INSTALLATION_ID);
    }

    @Test
    @DisplayName("ensureAllInstallationRepositoriesCovered adds missing monitors and prunes stale ones")
    void shouldReconcileMonitorsForInstallation() {
        Workspace workspace = persistWorkspace(RepositorySelection.ALL);
        repositoryToMonitorRepository.save(buildMonitor(workspace, "HephaestusTest/Orphaned"));

        when(repositoryEnumerator.enumerate(INSTALLATION_ID)).thenReturn(
            List.of(
                snapshot(1L, "HephaestusTest/demo-repository", "demo-repository", true),
                snapshot(2L, "HephaestusTest/payload-fixture-repo-renamed", "payload-fixture-repo-renamed", false)
            )
        );

        // Use deferSync=true to prevent async syncs from running during test
        // Async syncs can mark installations as suspended on 403 errors, affecting subsequent calls
        workspaceRepositoryMonitorService.ensureAllInstallationRepositoriesCovered(INSTALLATION_ID, null, true);

        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
        assertThat(monitors)
            .extracting(RepositoryToMonitor::getNameWithOwner)
            .containsExactlyInAnyOrder("HephaestusTest/demo-repository", "HephaestusTest/payload-fixture-repo-renamed");
        assertThat(repositoryRepository.findByNameWithOwner("HephaestusTest/demo-repository")).isPresent();
        assertThat(repositoryRepository.findByNameWithOwner("HephaestusTest/payload-fixture-repo-renamed")).isPresent();
        assertThat(repositoryRepository.findByNameWithOwner("HephaestusTest/Orphaned")).isEmpty();
    }

    @Test
    @DisplayName("ensureAllInstallationRepositoriesCovered runs for SELECTED installations")
    void shouldRespectSelectedInstallations() {
        Workspace workspace = persistWorkspace(RepositorySelection.SELECTED);

        when(repositoryEnumerator.enumerate(INSTALLATION_ID)).thenReturn(
            List.of(snapshot(3L, "HephaestusTest/HelloWorld", "HelloWorld", true))
        );

        // Use deferSync=true to prevent async syncs from running during test
        workspaceRepositoryMonitorService.ensureAllInstallationRepositoriesCovered(INSTALLATION_ID, null, true);

        List<RepositoryToMonitor> monitors = repositoryToMonitorRepository.findByWorkspaceId(workspace.getId());
        assertThat(monitors)
            .extracting(RepositoryToMonitor::getNameWithOwner)
            .containsExactly("HephaestusTest/HelloWorld");
    }

    private Workspace persistWorkspace(RepositorySelection selection) {
        return workspaceRepository.save(
            WorkspaceTestFixtures.installationWorkspace(INSTALLATION_ID, "HephaestusTest")
                .withRepositorySelection(selection)
                .withSlug("ws-install-" + INSTALLATION_ID + "-" + selection.name().toLowerCase(Locale.ENGLISH))
                .build()
        );
    }

    private RepositoryToMonitor buildMonitor(Workspace workspace, String nameWithOwner) {
        return WorkspaceTestFixtures.repositoryMonitor(workspace, nameWithOwner);
    }

    private GitHubInstallationRepositoryEnumerationService.InstallationRepositorySnapshot snapshot(
        long id,
        String nameWithOwner,
        String name,
        boolean isPrivate
    ) {
        return new GitHubInstallationRepositoryEnumerationService.InstallationRepositorySnapshot(
            id,
            nameWithOwner,
            name,
            isPrivate
        );
    }
}

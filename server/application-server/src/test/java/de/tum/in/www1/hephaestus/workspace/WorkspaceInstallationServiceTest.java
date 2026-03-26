package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationService;
import de.tum.in.www1.hephaestus.gitprovider.repository.RepositoryRepository;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsConsumerService;
import de.tum.in.www1.hephaestus.gitprovider.sync.NatsProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

@DisplayName("WorkspaceInstallationService")
class WorkspaceInstallationServiceTest extends BaseUnitTest {

    private static final long INSTALLATION_ID = 123L;

    @Mock
    private NatsProperties natsProperties;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private RepositoryRepository repositoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    @Mock
    private WorkspaceSlugService workspaceSlugService;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private NatsConsumerService natsConsumerService;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    @Mock
    private OrganizationService organizationService;

    private WorkspaceInstallationService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceInstallationService(
            natsProperties,
            workspaceRepository,
            repositoryToMonitorRepository,
            repositoryRepository,
            userRepository,
            gitProviderRepository,
            workspaceSlugService,
            workspaceMembershipService,
            natsConsumerService,
            gitHubAppTokenService,
            organizationService
        );

        lenient().when(natsProperties.enabled()).thenReturn(false);
        lenient()
            .when(workspaceRepository.save(any(Workspace.class)))
            .thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("handleAccountRename")
    class HandleAccountRename {

        @Test
        @DisplayName("case-only rename updates accountLogin on workspace")
        void caseOnlyRename_updatesAccountLogin() {
            Workspace workspace = createWorkspace("myorg");
            when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));

            service.handleAccountRename(INSTALLATION_ID, "myorg", "MyOrg");

            // The canonical casing from GitHub must be persisted
            ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
            verify(workspaceRepository).save(captor.capture());
            assertThat(captor.getValue().getAccountLogin()).isEqualTo("MyOrg");
        }

        @Test
        @DisplayName("case-only rename skips repository retargeting (equalsIgnoreCase guard)")
        void caseOnlyRename_skipsRepositoryRetargeting() {
            Workspace workspace = createWorkspace("myorg");
            when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));

            service.handleAccountRename(INSTALLATION_ID, "myorg", "MyOrg");

            // Repository retargeting should not query for repositories (old == new ignoring case)
            verify(repositoryRepository, never()).findByNameWithOwnerStartingWithIgnoreCase(any());
        }

        @Test
        @DisplayName("real rename updates accountLogin and retargets repositories")
        void realRename_updatesAndRetargets() {
            Workspace workspace = createWorkspace("old-org");
            workspace.setId(1L);
            when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));
            when(repositoryToMonitorRepository.findByWorkspaceId(1L)).thenReturn(Collections.emptyList());
            when(repositoryRepository.findByNameWithOwnerStartingWithIgnoreCase("old-org/")).thenReturn(
                Collections.emptyList()
            );

            service.handleAccountRename(INSTALLATION_ID, "old-org", "new-org");

            ArgumentCaptor<Workspace> captor = ArgumentCaptor.forClass(Workspace.class);
            verify(workspaceRepository).save(captor.capture());
            assertThat(captor.getValue().getAccountLogin()).isEqualTo("new-org");

            // Repository retargeting is attempted (different base names)
            verify(repositoryRepository).findByNameWithOwnerStartingWithIgnoreCase("old-org/");
        }

        @Test
        @DisplayName("identical login skips update entirely")
        void identicalLogin_skipsUpdate() {
            Workspace workspace = createWorkspace("MyOrg");
            when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(workspace));

            service.handleAccountRename(INSTALLATION_ID, "MyOrg", "MyOrg");

            // No save because login unchanged
            verify(workspaceRepository, never()).save(any());
        }

        @Test
        @DisplayName("blank newLogin is rejected")
        void blankNewLogin_skips() {
            service.handleAccountRename(INSTALLATION_ID, "old-org", "  ");

            verify(workspaceRepository, never()).findByInstallationId(any());
        }

        @Test
        @DisplayName("null newLogin is rejected")
        void nullNewLogin_skips() {
            service.handleAccountRename(INSTALLATION_ID, "old-org", null);

            verify(workspaceRepository, never()).findByInstallationId(any());
        }

        @Test
        @DisplayName("unknown installation is handled gracefully")
        void unknownInstallation_noOp() {
            when(workspaceRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

            service.handleAccountRename(INSTALLATION_ID, "old-org", "new-org");

            verify(workspaceRepository, never()).save(any());
        }
    }

    private Workspace createWorkspace(String accountLogin) {
        Workspace workspace = new Workspace();
        workspace.setAccountLogin(accountLogin);
        workspace.setWorkspaceSlug("test-workspace");
        return workspace;
    }
}

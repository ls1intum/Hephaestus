package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.gitprovider.common.GitProviderRepository;
import de.tum.cit.aet.hephaestus.integration.github.app.GitHubAppTokenService;
import de.tum.cit.aet.hephaestus.integration.github.lifecycle.GithubLifecycleListener;
import de.tum.cit.aet.hephaestus.integration.gitlab.common.GitLabProperties;
import de.tum.cit.aet.hephaestus.integration.registry.ConnectionConfig;
import de.tum.cit.aet.hephaestus.integration.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.gitprovider.user.AuthenticatedGitProviderUserService;
import de.tum.cit.aet.hephaestus.gitprovider.user.User;
import de.tum.cit.aet.hephaestus.gitprovider.user.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceProvisioningServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private WorkspaceService workspaceService;

    @Mock
    private GithubLifecycleListener githubLifecycleListener;

    @Mock
    private WorkspaceRepositoryMonitorService workspaceRepositoryMonitorService;

    @Mock
    private GitHubAppTokenService gitHubAppTokenService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitProviderRepository gitProviderRepository;

    @Mock
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Mock
    private WorkspaceMembershipService workspaceMembershipService;

    @Mock
    private WorkspaceScopeFilter workspaceScopeFilter;

    @Mock
    private AuthenticatedGitProviderUserService authenticatedGitProviderUserService;

    @Mock
    private ScmConnectionProvisioner scmConnectionProvisioner;

    private WorkspaceProvisioningService provisioningService;

    private WorkspaceProperties workspaceProperties;

    @BeforeEach
    void setUp() {
        workspaceProperties = new WorkspaceProperties(
            true,
            new WorkspaceProperties.DefaultProperties("aet-org", "pat-token", List.of()),
            false,
            null
        );

        var gitLabProperties = new GitLabProperties(
            "https://gitlab.com",
            Duration.ofSeconds(30),
            Duration.ofSeconds(60),
            Duration.ofMillis(200),
            Duration.ofMinutes(5)
        );

        provisioningService = new WorkspaceProvisioningService(
            workspaceProperties,
            workspaceRepository,
            repositoryToMonitorRepository,
            workspaceService,
            githubLifecycleListener,
            workspaceRepositoryMonitorService,
            gitHubAppTokenService,
            userRepository,
            gitProviderRepository,
            workspaceMembershipRepository,
            workspaceMembershipService,
            workspaceScopeFilter,
            gitLabProperties,
            authenticatedGitProviderUserService,
            scmConnectionProvisioner
        );
    }

    @Test
    void bootstrapDefaultPatWorkspace_addsAdminAsMember() throws Exception {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("aet-org");
        workspace.setDisplayName("AET Org");
        workspace.setId(1L);

        User owner = new User();
        owner.setId(11L);
        owner.setLogin("aet-org");
        owner.setName("Owner");
        owner.setAvatarUrl("https://example.com/avatar.png");
        owner.setHtmlUrl("https://example.com");
        owner.setType(User.Type.USER);

        User admin = new User();
        admin.setId(99L);
        admin.setLogin("admin");
        admin.setName("Admin");
        admin.setAvatarUrl("https://example.com/admin.png");
        admin.setHtmlUrl("https://example.com/admin");
        admin.setType(User.Type.USER);

        when(workspaceRepository.count()).thenReturn(0L);
        when(workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(1L, admin.getId())).thenReturn(
            Optional.empty()
        );
        when(
            workspaceService.createWorkspace(anyString(), anyString(), anyString(), any(AccountType.class), anyLong())
        ).thenReturn(workspace);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // User lookup for PAT bootstrap - user already exists
        when(userRepository.findByLogin("aet-org")).thenReturn(Optional.of(owner));
        when(userRepository.findByLogin("admin")).thenReturn(Optional.of(admin));

        provisioningService.bootstrapDefaultPatWorkspace();

        verify(workspaceMembershipService).createMembership(
            workspace,
            admin.getId(),
            WorkspaceMembership.WorkspaceRole.ADMIN
        );
        // Default admin handling should not throw and should not trigger redundant
        // workspace creations
        verify(workspaceService).createWorkspace(anyString(), anyString(), anyString(), any(), anyLong());

        // The PAT is no longer persisted on Workspace.personal_access_token — the
        // bootstrap path provisions a GitHub Connection row via ScmConnectionProvisioner
        // and the credential is stored encrypted on that row. Assert the call carried
        // the configured token.
        verify(scmConnectionProvisioner).provisionPatConnection(
            eq(workspace),
            eq(IntegrationKind.GITHUB),
            eq("pat"),
            any(ConnectionConfig.GitHubPatConfig.class),
            eq("pat-token"),
            anyString()
        );
        assertThat(workspace.getId()).isEqualTo(1L);
    }

    @Test
    void bootstrapDefaultPatWorkspace_noAdminPresent_doesNotInvokeMembershipCreation() {
        Workspace workspace = new Workspace();
        workspace.setWorkspaceSlug("aet-org");
        workspace.setDisplayName("AET Org");
        workspace.setId(1L);

        User owner = new User();
        owner.setId(11L);
        owner.setLogin("aet-org");
        owner.setName("Owner");
        owner.setAvatarUrl("https://example.com/avatar.png");
        owner.setHtmlUrl("https://example.com");
        owner.setType(User.Type.USER);

        when(workspaceRepository.count()).thenReturn(0L);
        when(
            workspaceService.createWorkspace(anyString(), anyString(), anyString(), any(AccountType.class), anyLong())
        ).thenReturn(workspace);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // User lookup for PAT bootstrap - user already exists
        when(userRepository.findByLogin("aet-org")).thenReturn(Optional.of(owner));
        when(userRepository.findByLogin("admin")).thenReturn(Optional.empty());

        provisioningService.bootstrapDefaultPatWorkspace();

        verify(workspaceMembershipService, never()).createMembership(
            any(Workspace.class),
            anyLong(),
            any(WorkspaceMembership.WorkspaceRole.class)
        );
    }

    @Test
    void ensureAdminMembershipIsAddedWhenWorkspaceAlreadyExists() {
        Workspace workspace = new Workspace();
        workspace.setId(42L);
        workspace.setWorkspaceSlug("ls1intum");
        workspace.setDisplayName("ls1intum");

        User admin = new User();
        admin.setId(99L);
        admin.setLogin("admin");
        admin.setName("Admin");
        admin.setAvatarUrl("https://example.com/admin.png");
        admin.setHtmlUrl("https://example.com/admin");
        admin.setType(User.Type.USER);

        when(workspaceRepository.count()).thenReturn(1L);
        when(workspaceRepository.findByWorkspaceSlug("aet-org")).thenReturn(Optional.empty());
        when(workspaceRepository.findAll()).thenReturn(java.util.List.of(workspace));
        when(userRepository.findByLogin("admin")).thenReturn(Optional.of(admin));
        when(workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(workspace.getId(), admin.getId())).thenReturn(
            Optional.empty()
        );

        provisioningService.bootstrapDefaultPatWorkspace();

        verify(workspaceMembershipService).createMembership(
            workspace,
            admin.getId(),
            WorkspaceMembership.WorkspaceRole.ADMIN
        );
        verify(workspaceService, never()).createWorkspace(anyString(), anyString(), anyString(), any(), anyLong());
    }
}

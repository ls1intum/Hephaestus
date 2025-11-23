package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.gitprovider.common.github.app.GitHubAppTokenService;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationSyncService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.github.GitHubUserSyncService;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kohsuke.github.GitHub;
import org.mockito.InjectMocks;
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
	private GitHubAppTokenService gitHubAppTokenService;

	@Mock
	private GitHubUserSyncService gitHubUserSyncService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private OrganizationSyncService organizationSyncService;

	@Mock
	private WorkspaceGitHubAccess workspaceGitHubAccess;

	@Mock
	private WorkspaceMembershipRepository workspaceMembershipRepository;

	@Mock
	private WorkspaceMembershipService workspaceMembershipService;

	private WorkspaceProvisioningService provisioningService;

	private WorkspaceProperties workspaceProperties;

	@BeforeEach
	void setUp() {
		workspaceProperties = new WorkspaceProperties();
		workspaceProperties.setInitDefault(true);
		workspaceProperties.getDefaultWorkspace().setLogin("aet-org");
		workspaceProperties.getDefaultWorkspace().setToken("pat-token");

		// Inject real properties via reflection because @InjectMocks cannot set them directly
		provisioningService = new WorkspaceProvisioningService(
			workspaceProperties,
			workspaceRepository,
			repositoryToMonitorRepository,
			workspaceService,
			gitHubAppTokenService,
			gitHubUserSyncService,
			userRepository,
			organizationSyncService,
			workspaceGitHubAccess,
			workspaceMembershipRepository,
			workspaceMembershipService,
			true
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
			when(workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(1L, admin.getId()))
				.thenReturn(Optional.empty());
		when(workspaceService.createWorkspace(
			anyString(),
			anyString(),
			anyString(),
			any(AccountType.class),
			anyLong()
		)).thenReturn(workspace);
		when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(gitHubUserSyncService.syncUser(any(GitHub.class), anyString())).thenReturn(owner);
		when(userRepository.findByLogin("admin")).thenReturn(Optional.of(admin));

		provisioningService.bootstrapDefaultPatWorkspace();

		verify(workspaceMembershipService)
			.createMembership(workspace, admin.getId(), WorkspaceMembership.WorkspaceRole.ADMIN);
		// Default admin handling should not throw and should not trigger redundant workspace creations
		verify(workspaceService).createWorkspace(anyString(), anyString(), anyString(), any(), anyLong());
		assertThat(workspace.getPersonalAccessToken()).isEqualTo("pat-token");
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
		when(workspaceService.createWorkspace(
			anyString(),
			anyString(),
			anyString(),
			any(AccountType.class),
			anyLong()
		)).thenReturn(workspace);
		when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(gitHubUserSyncService.syncUser(any(GitHub.class), anyString())).thenReturn(owner);
		when(userRepository.findByLogin("admin")).thenReturn(Optional.empty());

		provisioningService.bootstrapDefaultPatWorkspace();

		verify(workspaceMembershipService, never())
			.createMembership(any(Workspace.class), anyLong(), any(WorkspaceMembership.WorkspaceRole.class));
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
		when(workspaceMembershipRepository.findByWorkspace_IdAndUser_Id(workspace.getId(), admin.getId()))
			.thenReturn(Optional.empty());

		provisioningService.bootstrapDefaultPatWorkspace();

		verify(workspaceMembershipService)
			.createMembership(workspace, admin.getId(), WorkspaceMembership.WorkspaceRole.ADMIN);
		verify(workspaceService, never()).createWorkspace(anyString(), anyString(), anyString(), any(), anyLong());
	}
}

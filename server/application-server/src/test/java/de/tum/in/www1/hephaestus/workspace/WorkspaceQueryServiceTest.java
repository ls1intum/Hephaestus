package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.feature.FeatureFlagService;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabProperties;
import de.tum.in.www1.hephaestus.gitprovider.github.GitHubProperties;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class WorkspaceQueryServiceTest extends BaseUnitTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Mock
    private RepositoryToMonitorRepository repositoryToMonitorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GitHubProperties gitHubProperties;

    @Mock
    private GitLabProperties gitLabProperties;

    @Mock
    private FeatureFlagService featureFlagService;

    @Test
    void findAccessibleWorkspacesSortsByDisplayNameAndDeduplicatesMemberships() {
        Workspace alphaWorkspace = workspace(1L, "alpha-space", "Alpha Workspace", true);
        Workspace bravoWorkspace = workspace(2L, "bravo-space", "Bravo Workspace", false);
        Workspace zuluWorkspace = workspace(3L, "zulu-space", "Zulu Workspace", true);

        User currentUser = new User();
        currentUser.setId(42L);

        WorkspaceMembership bravoMembership = membership(bravoWorkspace);
        WorkspaceMembership alphaMembership = membership(alphaWorkspace);

        WorkspaceQueryService service = new WorkspaceQueryService(
            workspaceRepository,
            workspaceMembershipRepository,
            repositoryToMonitorRepository,
            userRepository,
            gitHubProperties,
            gitLabProperties,
            featureFlagService
        );

        when(workspaceRepository.findByStatusAndIsPubliclyViewableTrue(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(
            List.of(zuluWorkspace, alphaWorkspace)
        );
        when(workspaceMembershipRepository.findByUser_Id(42L)).thenReturn(List.of(bravoMembership, alphaMembership));
        when(workspaceRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(bravoWorkspace, alphaWorkspace));

        List<Workspace> workspaces = service.findAccessibleWorkspaces(Optional.of(currentUser));

        assertThat(workspaces)
            .extracting(Workspace::getWorkspaceSlug)
            .containsExactly("alpha-space", "bravo-space", "zulu-space");
    }

    @Test
    void findAccessibleWorkspacesSortsPublicWorkspacesForAnonymousUsers() {
        Workspace zuluWorkspace = workspace(3L, "zulu-space", "Zulu Workspace", true);
        Workspace alphaWorkspace = workspace(1L, "alpha-space", "Alpha Workspace", true);

        WorkspaceQueryService service = new WorkspaceQueryService(
            workspaceRepository,
            workspaceMembershipRepository,
            repositoryToMonitorRepository,
            userRepository,
            gitHubProperties,
            gitLabProperties,
            featureFlagService
        );

        when(workspaceRepository.findByStatusAndIsPubliclyViewableTrue(Workspace.WorkspaceStatus.ACTIVE)).thenReturn(
            List.of(zuluWorkspace, alphaWorkspace)
        );

        List<Workspace> workspaces = service.findAccessibleWorkspaces(Optional.empty());

        assertThat(workspaces).extracting(Workspace::getWorkspaceSlug).containsExactly("alpha-space", "zulu-space");
    }

    private Workspace workspace(Long id, String slug, String displayName, boolean publiclyViewable) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setWorkspaceSlug(slug);
        workspace.setDisplayName(displayName);
        workspace.setIsPubliclyViewable(publiclyViewable);
        workspace.setStatus(Workspace.WorkspaceStatus.ACTIVE);
        return workspace;
    }

    private WorkspaceMembership membership(Workspace workspace) {
        WorkspaceMembership membership = new WorkspaceMembership();
        membership.setWorkspace(workspace);
        membership.setId(new WorkspaceMembership.Id(workspace.getId(), 42L));
        return membership;
    }
}

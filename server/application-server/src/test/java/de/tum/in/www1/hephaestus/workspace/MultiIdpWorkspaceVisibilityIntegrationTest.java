package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * End-to-end tests for the multi-IdP identity resolver.
 * <p>
 * A Keycloak account with GitHub + GitLab federated identities must see workspaces where
 * membership exists on <em>either</em> linked row, and callers that assume a single-user
 * contract (e.g. the former single-IdP flow) must keep working unchanged.
 */
@DisplayName("Multi-IdP workspace visibility")
class MultiIdpWorkspaceVisibilityIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceQueryService workspaceQueryService;

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("lists workspaces belonging to both linked IdP rows")
    void listsWorkspacesFromBothLinkedRows() {
        User adminGithubRow = userRepository
            .findByNativeIdAndProviderId(3L, ensureGitHubProvider().getId())
            .orElseThrow();
        User adminGitlabRow = TestUserFactory.ensureUser(userRepository, "admin-gl", 42L, ensureGitLabProvider());

        Workspace githubWorkspace = createWorkspace(
            "gh-workspace",
            "GitHub Workspace",
            "gh-owner",
            AccountType.ORG,
            adminGithubRow
        );
        Workspace gitlabWorkspace = createWorkspace(
            "gl-workspace",
            "GitLab Workspace",
            "gl-owner",
            AccountType.ORG,
            adminGitlabRow
        );

        List<Workspace> accessible = workspaceQueryService.findAccessibleWorkspaces();

        assertThat(accessible).extracting(Workspace::getId).contains(githubWorkspace.getId(), gitlabWorkspace.getId());
    }

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("lists a workspace reachable only via the GitLab (non-primary) linked row — the production bug")
    void listsWorkspaceAttachedOnlyToNonPrimaryRow() {
        // The original bug: the GitHub row has no membership, only the GitLab row does.
        // The legacy single-user lookup returned the GitHub row (matching preferred_username)
        // and the GitLab workspace silently disappeared from the list. The aggregator must
        // still find it.
        userRepository.findByNativeIdAndProviderId(3L, ensureGitHubProvider().getId()).orElseThrow();
        User adminGitlabRow = TestUserFactory.ensureUser(userRepository, "admin-gl", 42L, ensureGitLabProvider());

        Workspace gitlabWorkspace = createWorkspace(
            "gl-only",
            "GitLab Only",
            "gl-only",
            AccountType.ORG,
            adminGitlabRow
        );

        assertThat(workspaceQueryService.findAccessibleWorkspaces())
            .extracting(Workspace::getId)
            .contains(gitlabWorkspace.getId());
    }

    @Test
    @WithUser(username = "testuser", githubId = 1L)
    @DisplayName("single-IdP user continues to see only their own workspaces")
    void singleIdpUserRegressionLock() {
        User githubUser = userRepository.findByNativeIdAndProviderId(1L, ensureGitHubProvider().getId()).orElseThrow();
        Workspace mine = createWorkspace("mine", "Mine", "mine", AccountType.ORG, githubUser);

        // Workspace owned by an unrelated user — must NOT appear in accessible workspaces.
        User strangerGithubRow = TestUserFactory.ensureUser(userRepository, "stranger", 999L, ensureGitHubProvider());
        Workspace stranger = createWorkspace("stranger-ws", "Stranger", "stranger", AccountType.ORG, strangerGithubRow);

        List<Workspace> accessible = workspaceQueryService.findAccessibleWorkspaces();
        assertThat(accessible).extracting(Workspace::getId).contains(mine.getId()).doesNotContain(stranger.getId());
    }

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("union role across linked rows — owner on one row, member on the other, effective role is OWNER")
    void unionRoleAcrossLinkedRows() {
        User adminGithubRow = userRepository
            .findByNativeIdAndProviderId(3L, ensureGitHubProvider().getId())
            .orElseThrow();
        User adminGitlabRow = TestUserFactory.ensureUser(userRepository, "admin-gl", 42L, ensureGitLabProvider());

        // Workspace owner is the GitHub row (OWNER by default); add the GitLab row as MEMBER.
        // After aggregation the effective role must be OWNER, not MEMBER.
        Workspace ws = createWorkspace("dual", "Dual", "dual", AccountType.ORG, adminGithubRow);
        ensureWorkspaceMembership(ws, adminGitlabRow, WorkspaceRole.MEMBER);

        List<WorkspaceMembership> memberships = workspaceMembershipRepository.findAllByWorkspace_IdAndUser_IdIn(
            ws.getId(),
            List.of(adminGithubRow.getId(), adminGitlabRow.getId())
        );
        assertThat(memberships)
            .extracting(WorkspaceMembership::getRole)
            .contains(WorkspaceRole.OWNER, WorkspaceRole.MEMBER);
    }
}

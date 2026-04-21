package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * End-to-end tests for the multi-IdP identity resolver.
 * <p>
 * A Keycloak account with GitHub + GitLab federated identities must see workspaces where
 * membership exists on <em>either</em> linked row, and callers that assume a single-user
 * contract (e.g. the former single-IdP flow) must keep working unchanged.
 */
@AutoConfigureWebTestClient
@DisplayName("Multi-IdP workspace visibility")
class MultiIdpWorkspaceVisibilityIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceQueryService workspaceQueryService;

    @Autowired
    private WebTestClient webTestClient;

    private User requireSeededGitHubUser(String login, long nativeId) {
        return TestUserFactory.ensureUser(userRepository, login, nativeId, ensureGitHubProvider());
    }

    private String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("lists workspaces belonging to both linked IdP rows")
    void listsWorkspacesFromBothLinkedRows() {
        User adminGithubRow = requireSeededGitHubUser("admin", 3L);
        User adminGitlabRow = TestUserFactory.ensureUser(
            userRepository,
            unique("admin-gl"),
            42L,
            ensureGitLabProvider()
        );
        String githubSlug = unique("gh-workspace");
        String gitlabSlug = unique("gl-workspace");

        Workspace githubWorkspace = createWorkspace(
            githubSlug,
            "GitHub Workspace",
            unique("gh-owner"),
            AccountType.ORG,
            adminGithubRow
        );
        Workspace gitlabWorkspace = createWorkspace(
            gitlabSlug,
            "GitLab Workspace",
            unique("gl-owner"),
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
        requireSeededGitHubUser("admin", 3L);
        User adminGitlabRow = TestUserFactory.ensureUser(
            userRepository,
            unique("admin-gl"),
            42L,
            ensureGitLabProvider()
        );
        String gitlabSlug = unique("gl-only");

        Workspace gitlabWorkspace = createWorkspace(
            gitlabSlug,
            "GitLab Only",
            unique("gl-only"),
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
        User githubUser = requireSeededGitHubUser("testuser", 1L);
        Workspace mine = createWorkspace(unique("mine"), "Mine", unique("mine"), AccountType.ORG, githubUser);

        // Workspace owned by an unrelated user — must NOT appear in accessible workspaces.
        User strangerGithubRow = TestUserFactory.ensureUser(
            userRepository,
            unique("stranger"),
            999L,
            ensureGitHubProvider()
        );
        Workspace stranger = createWorkspace(
            unique("stranger-ws"),
            "Stranger",
            unique("stranger"),
            AccountType.ORG,
            strangerGithubRow
        );

        List<Workspace> accessible = workspaceQueryService.findAccessibleWorkspaces();
        assertThat(accessible).extracting(Workspace::getId).contains(mine.getId()).doesNotContain(stranger.getId());
    }

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("union role across linked rows — owner on one row, member on the other, effective role is OWNER")
    void unionRoleAcrossLinkedRows() {
        User adminGithubRow = requireSeededGitHubUser("admin", 3L);
        User adminGitlabRow = TestUserFactory.ensureUser(
            userRepository,
            unique("admin-gl"),
            42L,
            ensureGitLabProvider()
        );

        // Workspace owner is the GitHub row (OWNER by default); add the GitLab row as MEMBER.
        // After aggregation the effective role must be OWNER, not MEMBER.
        Workspace ws = createWorkspace(unique("dual"), "Dual", unique("dual"), AccountType.ORG, adminGithubRow);
        ensureWorkspaceMembership(ws, adminGitlabRow, WorkspaceRole.MEMBER);

        WorkspaceMembershipDTO membership = webTestClient
            .get()
            .uri("/workspaces/{slug}/members/me", ws.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceMembershipDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(membership).isNotNull();
        assertThat(membership.role()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @WithAdminUser(username = "admin", githubId = 3L, gitlabId = 42L)
    @DisplayName("lists workspaces through the HTTP registry endpoint across linked rows")
    void listsAccessibleWorkspacesThroughHttpEndpoint() {
        User adminGithubRow = requireSeededGitHubUser("admin", 3L);
        User adminGitlabRow = TestUserFactory.ensureUser(
            userRepository,
            unique("admin-gl"),
            42L,
            ensureGitLabProvider()
        );
        String githubSlug = unique("gh-visible");
        String gitlabSlug = unique("gl-visible");

        createWorkspace(githubSlug, "GitHub Visible", unique("gh-visible"), AccountType.ORG, adminGithubRow);
        createWorkspace(gitlabSlug, "GitLab Visible", unique("gl-visible"), AccountType.ORG, adminGitlabRow);

        webTestClient
            .get()
            .uri("/workspaces")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.[*].workspaceSlug")
            .value(slugs -> {
                assertThat(slugs).isInstanceOf(List.class);
                @SuppressWarnings("unchecked")
                List<String> workspaceSlugs = (List<String>) slugs;
                assertThat(workspaceSlugs).contains(githubSlug, gitlabSlug);
            });
    }
}

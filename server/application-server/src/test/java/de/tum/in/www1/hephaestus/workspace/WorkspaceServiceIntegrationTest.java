package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHRepositorySelection;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("Workspace service integration")
class WorkspaceServiceIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Test
    @DisplayName("createWorkspace normalizes slug and assigns owner membership")
    void createWorkspaceAssignsOwnerMembership() {
        User owner = persistUser("OwnerLogin");

        Workspace workspace = createWorkspace("Acme Org", "Acme Org", "acme", AccountType.ORG, owner);

        assertThat(workspace.getWorkspaceSlug()).isEqualTo("acme-org");
        assertThat(workspace.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);
        assertThat(workspace.getAccountType()).isEqualTo(AccountType.ORG);

        var membership = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), owner.getId())
            .orElseThrow();
        assertThat(membership.getRole()).isEqualTo(WorkspaceMembership.WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("updateNotifications persists Slack configuration and enforces channel validation")
    void updateNotificationsPersistsStateAndValidatesChannel() {
        User owner = persistUser("notification-owner");
        Workspace workspace = createWorkspace(
            "notification-space",
            "Notification Space",
            "notification",
            AccountType.ORG,
            owner
        );

        workspaceService.updateNotifications(workspace.getWorkspaceSlug(), true, "core-team", "C12345678");

        Workspace updated = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(updated.getLeaderboardNotificationEnabled()).isTrue();
        assertThat(updated.getLeaderboardNotificationTeam()).isEqualTo("core-team");
        assertThat(updated.getLeaderboardNotificationChannelId()).isEqualTo("C12345678");

        assertThatThrownBy(() ->
            workspaceService.updateNotifications(workspace.getWorkspaceSlug(), true, null, "invalid")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Slack channel ID");
    }

    @Test
    @DisplayName("Workspace lifecycle enforces suspend/resume/purge transitions")
    void workspaceLifecycleTransitions() {
        User owner = persistUser("lifecycle-owner");
        Workspace workspace = createWorkspace("Lifecycle", "Lifecycle", "lifecycle", AccountType.ORG, owner);

        workspaceLifecycleService.suspendWorkspace(workspace.getWorkspaceSlug());
        Workspace suspended = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(suspended.getStatus()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED);

        workspaceLifecycleService.resumeWorkspace(workspace.getWorkspaceSlug());
        Workspace resumed = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(resumed.getStatus()).isEqualTo(Workspace.WorkspaceStatus.ACTIVE);

        workspaceLifecycleService.updateStatus(workspace.getWorkspaceSlug(), Workspace.WorkspaceStatus.SUSPENDED);
        Workspace patched = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(patched.getStatus()).isEqualTo(Workspace.WorkspaceStatus.SUSPENDED);

        workspaceLifecycleService.purgeWorkspace(workspace.getWorkspaceSlug());
        Workspace purged = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(purged.getStatus()).isEqualTo(Workspace.WorkspaceStatus.PURGED);

        assertThatThrownBy(() -> workspaceLifecycleService.resumeWorkspace(workspace.getWorkspaceSlug()))
            .isInstanceOf(WorkspaceLifecycleViolationException.class)
            .hasMessageContaining("purged");
    }

    @Test
    @DisplayName("PAT workspace without token is promoted to GitHub App installation during provisioning")
    void patWorkspaceWithoutTokenIsPromoted() {
        User owner = persistUser("ls1intum-owner");
        Workspace workspace = createWorkspace("ls1intum", "ls1intum", "ls1intum", AccountType.ORG, owner);

        // Explicitly ensure PAT mode without token to mirror legacy bootstrap behaviour
        workspace.setGitProviderMode(Workspace.GitProviderMode.PAT_ORG);
        workspace.setPersonalAccessToken(null);
        workspaceRepository.save(workspace);

        Workspace promoted = workspaceService.ensureForInstallation(95711017L, "ls1intum", GHRepositorySelection.ALL);

        Workspace persisted = workspaceRepository.findById(promoted.getId()).orElseThrow();

        assertThat(promoted.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        assertThat(promoted.getInstallationId()).isEqualTo(95711017L);
        assertThat(promoted.getPersonalAccessToken()).isNull();
        assertThat(promoted.getInstallationLinkedAt()).isNotNull();

        assertThat(persisted.getGitProviderMode()).isEqualTo(Workspace.GitProviderMode.GITHUB_APP_INSTALLATION);
        assertThat(persisted.getInstallationId()).isEqualTo(95711017L);
    }
}

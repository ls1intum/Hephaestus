package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.integration.core.connection.ConnectionService;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.workspace.exception.WorkspaceLifecycleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceServiceIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WorkspaceLifecycleService workspaceLifecycleService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Autowired
    private ConnectionService connectionService;

    @Test
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
    void updateReviewCyclePersistsDayAndTime() {
        User owner = persistUser("review-cycle-owner");
        Workspace workspace = createWorkspace("review-cycle", "Review Cycle", "review-cycle", AccountType.ORG, owner);

        workspaceService.updateReviewCycle(workspace.getWorkspaceSlug(), 3, "08:30");

        Workspace updated = workspaceRepository.findById(workspace.getId()).orElseThrow();
        assertThat(updated.getReviewCycleDay()).isEqualTo(3);
        assertThat(updated.getReviewCycleTime()).isEqualTo("08:30");
    }

    @Test
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
    void patWorkspaceWithoutTokenIsPromoted() {
        User owner = persistUser("ls1intum-owner");
        Workspace workspace = createWorkspace("ls1intum", "ls1intum", "ls1intum", AccountType.ORG, owner);

        // No PAT Connection on the workspace — this mirrors the legacy
        // "PAT_ORG without token" shape the migration replaces. The lifecycle listener
        // should promote it cleanly to a GitHub App Connection.

        Workspace promoted = githubLifecycleListener.createOrUpdateFromInstallation(
            95711017L,
            "ls1intum",
            RepositorySelection.ALL
        );

        // provider mode + installation id live on the Connection
        // registry now, not on Workspace.
        assertThat(connectionService.findActiveProviderKind(promoted.getId())).hasValue(IntegrationKind.GITHUB);
        assertThat(connectionService.findActiveGitHubAppConfig(promoted.getId())).hasValueSatisfying(cfg ->
            assertThat(cfg.installationId()).isEqualTo(95711017L)
        );
        assertThat(connectionService.findActiveBearerToken(promoted.getId(), IntegrationKind.GITHUB))
            .as("App-mode Connections do not store a bearer credential blob")
            .isEmpty();
    }
}

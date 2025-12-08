package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import de.tum.in.www1.hephaestus.gitprovider.github.BaseGitHubLiveIntegrationTest;
import de.tum.in.www1.hephaestus.gitprovider.organization.OrganizationMembershipRepository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WorkspaceMembershipSyncGitHubLiveIntegrationTest extends BaseGitHubLiveIntegrationTest {

    @Autowired
    private WorkspaceService workspaceService;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private OrganizationMembershipRepository organizationMembershipRepository;

    @Autowired
    private WorkspaceMembershipRepository workspaceMembershipRepository;

    @Test
    void syncsOrganizationMembersIntoWorkspaceMemberships() {
        // provisioning runs on context load; trigger monitoring pipeline (includes members sync)
        workspaceService.activateAllWorkspaces();
        Workspace workspace = workspaceRepository.findAll().stream().findFirst().orElseThrow();

        await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> {
                List<Long> orgMembers = organizationMembershipRepository.findUserIdsByOrganizationId(
                    workspace.getOrganization().getId()
                );
                List<WorkspaceMembership> workspaceMembers = workspaceMembershipRepository.findByWorkspace_Id(
                    workspace.getId()
                );

                assertThat(orgMembers).as("organization members should be present").isNotEmpty();
                assertThat(workspaceMembers)
                    .as("workspace members should mirror organization members")
                    .hasSize(orgMembers.size());
                assertThat(
                    workspaceMembers.stream().map(WorkspaceMembership::getUser).map(User::getId).toList()
                ).containsAll(orgMembers);
            });
    }
}

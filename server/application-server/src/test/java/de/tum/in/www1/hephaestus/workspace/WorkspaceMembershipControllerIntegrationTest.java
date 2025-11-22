package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.TestUserFactory;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithUser;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.dto.AssignRoleRequestDTO;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceMembershipDTO;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@AutoConfigureWebTestClient
@DisplayName("Workspace membership controller integration")
class WorkspaceMembershipControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithAdminUser
    void listMembersReturnsAllWorkspaceMembersForAdmin() {
        User owner = persistUser("membership-owner");
        Workspace workspace = createWorkspace(
            "membership-space",
            "Membership Space",
            "membership",
            AccountType.ORG,
            owner
        );

        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L);
        workspaceMembershipService.createMembership(workspace, adminUser.getId(), WorkspaceRole.ADMIN);

        User member = persistUser("membership-member");
        workspaceMembershipService.createMembership(workspace, member.getId(), WorkspaceRole.MEMBER);

        List<WorkspaceMembershipDTO> memberships = webTestClient
            .get()
            .uri("/workspaces/{slug}/members", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBodyList(WorkspaceMembershipDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(memberships).isNotNull();
        assertThat(memberships)
            .extracting(WorkspaceMembershipDTO::userLogin)
            .containsExactlyInAnyOrder("admin", "membership-member", "membership-owner");
    }

    @Test
    @WithAdminUser
    void adminCanAssignRoleToMember() {
        User owner = persistUser("membership-owner-2");
        Workspace workspace = createWorkspace(
            "membership-space-2",
            "Membership Space 2",
            "membership2",
            AccountType.ORG,
            owner
        );

        User adminUser = TestUserFactory.ensureUser(userRepository, "admin", 3L);
        workspaceMembershipService.createMembership(workspace, adminUser.getId(), WorkspaceRole.ADMIN);

        User targetUser = persistUser("target-user");
        workspaceMembershipService.createMembership(workspace, targetUser.getId(), WorkspaceRole.MEMBER);

        AssignRoleRequestDTO request = new AssignRoleRequestDTO(targetUser.getId(), WorkspaceRole.ADMIN);

        WorkspaceMembershipDTO response = webTestClient
            .post()
            .uri("/workspaces/{slug}/members/assign", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceMembershipDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.role()).isEqualTo(WorkspaceRole.ADMIN);

        WorkspaceMembership updated = workspaceMembershipRepository
            .findByWorkspace_IdAndUser_Id(workspace.getId(), targetUser.getId())
            .orElseThrow();
        assertThat(updated.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    @WithUser(username = "outsider", authorities = "mentor_access")
    void nonMembersCannotAccessMembershipEndpoints() {
        User owner = persistUser("membership-owner-3");
        Workspace workspace = createWorkspace(
            "membership-space-3",
            "Membership Space 3",
            "membership3",
            AccountType.ORG,
            owner
        );

        persistUser("outsider");
        // outsider is intentionally not added to the workspace

        webTestClient
            .get()
            .uri("/workspaces/{slug}/members", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}

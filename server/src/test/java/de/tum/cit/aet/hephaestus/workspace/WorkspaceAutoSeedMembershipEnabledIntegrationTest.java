package de.tum.cit.aet.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.testconfig.WorkspaceEchoControllers;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Verifies the dev/e2e auto-seed path: when {@code hephaestus.workspace.auto-seed-membership=true},
 * the first authenticated visitor of a zero-membership workspace IS seeded as ADMIN. Production keeps the
 * flag false (see {@link WorkspaceContextFilterIntegrationTest#autoSeedDisabledDoesNotGrantAdminToFirstVisitorOfEmptyMembershipWorkspace}).
 */
@TestPropertySource(properties = "hephaestus.workspace.auto-seed-membership=true")
class WorkspaceAutoSeedMembershipEnabledIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Test
    @WithMentorUser
    void autoSeedEnabledGrantsAdminToFirstVisitorOfEmptyMembershipWorkspace() {
        User visitor = persistUser("mentor");
        User workspaceOwner = persistUser("auto-seed-owner");
        Workspace workspace = createWorkspace("auto-seed", "AutoSeed", "autoseed", AccountType.ORG, workspaceOwner);
        // Strip the seeded OWNER membership so the workspace is genuinely zero-membership.
        workspaceMembershipRepository.deleteAll(workspaceMembershipRepository.findByWorkspace_Id(workspace.getId()));
        assertThat(workspaceMembershipRepository.findByWorkspace_Id(workspace.getId())).isEmpty();

        WorkspaceEchoControllers.WorkspaceContextSnapshot response = Objects.requireNonNull(
            webTestClient
                .get()
                .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
                .headers(TestAuthUtils.withCurrentUser())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(WorkspaceEchoControllers.WorkspaceContextSnapshot.class)
                .returnResult()
                .getResponseBody()
        );

        assertThat(response.roles()).containsExactly("ADMIN");
        assertThat(
            workspaceMembershipRepository.findByWorkspace_IdAndUser_IdIn(workspace.getId(), Set.of(visitor.getId()))
        )
            .as("auto-seed enabled must create exactly one ADMIN membership for the first visitor")
            .hasSize(1);
    }

    @Autowired
    private WebTestClient webTestClient;
}

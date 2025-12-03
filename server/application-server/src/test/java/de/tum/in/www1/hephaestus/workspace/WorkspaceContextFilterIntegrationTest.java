package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.Workspace.WorkspaceStatus;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.dto.WorkspaceDTO;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AutoConfigureWebTestClient
@Import(WorkspaceContextFilterIntegrationTest.WorkspaceContextEchoController.class)
class WorkspaceContextFilterIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Test
    @WithAdminUser
    void workspaceContextIsInjectedForMembers() {
        User owner = persistUser("admin");
        Workspace workspace = createWorkspace("alpha-space", "Alpha", "alpha", AccountType.ORG, owner);
        ensureAdminMembership(workspace);

        WorkspaceContextSnapshot response = requestContextEcho(workspace.getWorkspaceSlug());

        assertThat(response.contextSlug()).isEqualTo(workspace.getWorkspaceSlug());
        assertThat(response.contextId()).isEqualTo(workspace.getId());
        assertThat(response.roles()).containsExactly("OWNER");
    }

    @Test
    @WithAdminUser
    void invalidSlugReturnsBadRequestProblem() {
        persistUser("admin");

        ProblemDetail problem = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", "INVALID-SLUG")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isBadRequest()
            .expectBody(ProblemDetail.class)
            .returnResult()
            .getResponseBody();

        assertThat(problem).isNotNull();
        assertThat(problem.getDetail()).contains("Invalid workspace slug");
    }

    @Test
    @WithMentorUser
    void nonMembersAreForbiddenFromAccessingWorkspace() {
        persistUser("mentor");
        User workspaceOwner = persistUser("workspace-owner");
        Workspace workspace = createWorkspace("beta-space", "Beta", "beta", AccountType.ORG, workspaceOwner);

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithAdminUser
    void adminWithoutMembershipIsAlsoForbidden() {
        persistUser("admin");
        User workspaceOwner = persistUser("admin-workspace-owner");
        Workspace workspace = createWorkspace("gamma-space", "Gamma", "gamma", AccountType.ORG, workspaceOwner);

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithAdminUser
    void workspaceContextDoesNotLeakBetweenRequests() {
        User owner = persistUser("admin");
        Workspace first = createWorkspace("gamma-space", "Gamma", "gamma", AccountType.ORG, owner);
        Workspace second = createWorkspace("delta-space", "Delta", "delta", AccountType.ORG, owner);

        WorkspaceContextSnapshot firstResponse = requestContextEcho(first.getWorkspaceSlug());
        WorkspaceContextSnapshot secondResponse = requestContextEcho(second.getWorkspaceSlug());

        assertThat(firstResponse.contextSlug()).isEqualTo(first.getWorkspaceSlug());
        assertThat(secondResponse.contextSlug()).isEqualTo(second.getWorkspaceSlug());
        assertThat(secondResponse.contextSlug()).isNotEqualTo(firstResponse.contextSlug());
    }

    @Test
    @WithAdminUser
    void suspendedWorkspaceBlocksScopedChildRoutes() {
        User owner = persistUser("admin");
        Workspace workspace = createWorkspace("omega-space", "Omega", "omega", AccountType.ORG, owner);
        ensureAdminMembership(workspace);

        workspace.setStatus(WorkspaceStatus.SUSPENDED);
        workspaceRepository.save(workspace);

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void suspendedWorkspaceAllowsBaseReadRequests() {
        User owner = persistUser("admin");
        Workspace workspace = createWorkspace("zeta-space", "Zeta", "zeta", AccountType.ORG, owner);
        ensureAdminMembership(workspace);

        workspace.setStatus(WorkspaceStatus.SUSPENDED);
        workspaceRepository.save(workspace);

        WorkspaceDTO response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceDTO.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.workspaceSlug()).isEqualTo(workspace.getWorkspaceSlug());
    }

    @Test
    void unauthenticatedWorkspaceListIsUnauthorized() {
        webTestClient.get().uri("/workspaces").exchange().expectStatus().isUnauthorized();
        webTestClient.get().uri("/workspaces/").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unauthenticatedAccessToPublicWorkspaceIsAllowed() {
        User owner = persistUser("public-owner");
        Workspace workspace = createWorkspace("public-space", "Public", "public", AccountType.ORG, owner);
        workspaceService.updatePublicVisibility(workspace.getWorkspaceSlug(), true);

        WorkspaceContextSnapshot response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceContextSnapshot.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.contextSlug()).isEqualTo(workspace.getWorkspaceSlug());
        assertThat(response.roles()).isEmpty(); // anonymous viewer
    }

    @Test
    void unauthenticatedAccessToPrivateWorkspaceIsUnauthorized() {
        User owner = persistUser("private-owner");
        Workspace workspace = createWorkspace("private-space", "Private", "private", AccountType.ORG, owner);
        workspaceService.updatePublicVisibility(workspace.getWorkspaceSlug(), false);

        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    private WorkspaceContextSnapshot requestContextEcho(String slug) {
        WorkspaceContextSnapshot response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/context-echo", slug)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(WorkspaceContextSnapshot.class)
            .returnResult()
            .getResponseBody();

        return Objects.requireNonNull(response, "Expected workspace context payload");
    }

    record WorkspaceContextSnapshot(String pathSlug, String contextSlug, Long contextId, List<String> roles) {}

    @RestController
    @RequestMapping("/workspaces/{workspaceSlug}/context-echo")
    static class WorkspaceContextEchoController {

        @GetMapping
        ResponseEntity<WorkspaceContextSnapshot> echo(
            @PathVariable String workspaceSlug,
            WorkspaceContext workspaceContext
        ) {
            WorkspaceContextSnapshot snapshot;
            if (workspaceContext == null) {
                snapshot = new WorkspaceContextSnapshot(workspaceSlug, null, null, List.of());
            } else {
                snapshot = new WorkspaceContextSnapshot(
                    workspaceSlug,
                    workspaceContext.slug(),
                    workspaceContext.id(),
                    workspaceContext.roles().stream().map(Enum::name).toList()
                );
            }
            return ResponseEntity.ok(snapshot);
        }
    }
}

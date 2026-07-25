package de.tum.cit.aet.hephaestus.agent;

import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Pins the one ambiguity the {@code /agents} namespace creates: {@code GET /agents/jobs} (a literal
 * segment on {@code AgentJobController}) and {@code GET /agents/{purpose}} (a template on
 * {@code AgentBindingController}) are both two-segment patterns under the same parent.
 *
 * <p>Spring's {@code PathPattern} specificity comparator ranks a literal above a variable, so
 * {@code jobs} can never be swallowed as a purpose. That is load-bearing rather than incidental: if the
 * ordering ever flipped, {@code /agents/jobs} would reach the binding controller and fail enum
 * conversion, turning the whole job-history screen into a 400 that no unit test would catch — both
 * controllers would still pass their own suites in isolation.
 *
 * <p>Both assertions matter. The first proves the literal wins; the second proves the template is still
 * reachable and still rejects a non-purpose, i.e. the win is precedence and not a route that shadows
 * its sibling entirely.
 */
class AgentsPathDispatchTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    private Workspace setupWorkspace() {
        User owner = persistUser("dispatch-owner");
        Workspace workspace = createWorkspace(
            "dispatch-ws",
            "Dispatch Workspace",
            "dispatch-org",
            AccountType.ORG,
            owner
        );
        ensureAdminMembership(workspace);
        return workspace;
    }

    @Test
    @WithAdminUser
    @DisplayName("GET /agents/jobs reaches the job controller, not /agents/{purpose}")
    void jobsSegmentOutranksThePurposeTemplate() {
        Workspace workspace = setupWorkspace();

        // A paginated envelope can only have come from AgentJobController; the binding controller
        // returns a bare array, and an attempt to bind "jobs" to AgentPurpose would have been a 400.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents/jobs", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.content")
            .isArray()
            .jsonPath("$.totalElements")
            .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    @DisplayName("the sibling collection GET /agents is still its own route")
    void agentsCollectionIsNotShadowedByTheJobsRoute() {
        Workspace workspace = setupWorkspace();

        // A bare array is the binding controller's shape. If /agents/jobs had claimed the parent, or the
        // two mappings had collided at startup, this would not be reachable at all.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$")
            .isArray();
    }

    @Test
    @WithAdminUser
    @DisplayName("PUT /agents/{purpose} rejects a value that is not a purpose")
    void purposeTemplateStillValidatesItsEnum() {
        Workspace workspace = setupWorkspace();

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agents/not-a-purpose", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", 1))
            .exchange()
            .expectStatus()
            .is4xxClientError();
    }
}

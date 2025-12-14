package de.tum.in.www1.hephaestus.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithAdminUser;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@AutoConfigureWebTestClient
@Import(WorkspaceScopedControllerIntegrationTest.ScopedEchoController.class)
class WorkspaceScopedControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @WithAdminUser
    void scopedControllerRequiresWorkspaceSlugAndInjectsContext() {
        User owner = persistUser("admin");
        Workspace workspace = createWorkspace("scoped-alpha", "Scoped Alpha", "alpha", AccountType.ORG, owner);
        ensureAdminMembership(workspace);

        ScopedEcho response = webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/scoped-test/echo", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(ScopedEcho.class)
            .returnResult()
            .getResponseBody();

        assertThat(response).isNotNull();
        assertThat(response.requestPath()).endsWith(
            "/workspaces/" + workspace.getWorkspaceSlug() + "/scoped-test/echo"
        );
        assertThat(response.contextSlug()).isEqualTo(workspace.getWorkspaceSlug());
        assertThat(response.contextId()).isEqualTo(workspace.getId());
        assertThat(response.roles()).containsExactly("OWNER");
    }

    @Test
    @WithAdminUser
    void scopedControllerIsNotReachableWithoutWorkspaceSlug() {
        webTestClient
            .get()
            .uri("/scoped-test/echo")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    @Test
    @WithAdminUser
    void unknownWorkspaceSlugReturnsNotFoundBeforeControllerExecutes() {
        webTestClient
            .get()
            .uri("/workspaces/{workspaceSlug}/scoped-test/echo", "missing")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNotFound();
    }

    record ScopedEcho(String requestPath, String contextSlug, Long contextId, List<String> roles) {}

    @WorkspaceScopedController
    @RequestMapping("/scoped-test")
    static class ScopedEchoController {

        @GetMapping("/echo")
        ResponseEntity<ScopedEcho> echo(HttpServletRequest request, WorkspaceContext context) {
            ScopedEcho payload = new ScopedEcho(
                request.getRequestURI(),
                context != null ? context.slug() : null,
                context != null ? context.id() : null,
                context != null ? context.roles().stream().map(Enum::name).toList() : List.of()
            );
            return ResponseEntity.ok(payload);
        }
    }
}

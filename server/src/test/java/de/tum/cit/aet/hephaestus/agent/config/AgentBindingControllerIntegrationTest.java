package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnection;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmConnectionRepository;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModel;
import de.tum.cit.aet.hephaestus.agent.catalog.LlmModelRepository;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import de.tum.cit.aet.hephaestus.testconfig.LlmCatalogTestFixtures;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithAdminUser;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The per-purpose binding endpoints over the real stack.
 *
 * <p>Exists mainly for the listing: it reports each binding's {@code ready}, which resolves the bound
 * model → connection AFTER the loading transaction closed (readiness is judged outside a transaction
 * on purpose — resolve() signals a revoked model by throwing, and catching that inside a shared
 * transaction would still mark it rollback-only). A plain finder there returns rows whose LAZY model
 * blows up on the first touch, so the endpoint 500s with LazyInitializationException instead of
 * answering. Unit tests cannot see it — they mock the repository and never cross a session boundary.
 */
class AgentBindingControllerIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private LlmConnectionRepository llmConnectionRepository;

    @Autowired
    private LlmModelRepository llmModelRepository;

    private Workspace setupWorkspace(String slug) {
        User owner = persistUser(slug + "-owner");
        Workspace workspace = createWorkspace(slug, "Binding Workspace", slug + "-org", AccountType.ORG, owner);
        ensureAdminMembership(workspace);
        return workspace;
    }

    private LlmModel seedInstanceModel(String slug) {
        LlmConnection connection = llmConnectionRepository.save(LlmCatalogTestFixtures.connection(slug));
        return llmModelRepository.save(LlmCatalogTestFixtures.model(connection, slug + "-model", "gpt-binding-test"));
    }

    @Test
    @WithAdminUser
    @DisplayName("listing bindings reports readiness without tripping over the bound model's lazy associations")
    void listingReportsReadinessForAFreshlyLoadedBinding() {
        Workspace workspace = setupWorkspace("binding-list");
        LlmModel model = seedInstanceModel("binding-list");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), "PRACTICE_DETECTION")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", true))
            .exchange()
            .expectStatus()
            .isOk();

        // The listing loads the binding fresh — the write above cannot have left it hydrated in a
        // session, which is exactly the path that regressed.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(1)
            .jsonPath("$[0].purpose")
            .isEqualTo("PRACTICE_DETECTION")
            .jsonPath("$[0].instanceModelId")
            .isEqualTo(model.getId())
            .jsonPath("$[0].ready")
            .isEqualTo(true);
    }

    @Test
    @WithAdminUser
    @DisplayName("a disabled binding is listed but not ready, and deleting it turns the purpose off")
    void disabledBindingIsNotReadyAndCanBeDeleted() {
        Workspace workspace = setupWorkspace("binding-off");
        LlmModel model = seedInstanceModel("binding-off");

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), "MENTOR")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", false))
            .exchange()
            .expectStatus()
            .isOk();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$[0].enabled")
            .isEqualTo(false)
            .jsonPath("$[0].ready")
            .isEqualTo(false);

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), "MENTOR")
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isNoContent();

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .jsonPath("$.length()")
            .isEqualTo(0);
    }

    @Test
    @WithAdminUser
    @DisplayName("binding a model the workspace cannot use is rejected")
    void bindingAnUnavailableModelIsRejected() {
        Workspace workspace = setupWorkspace("binding-bad");
        LlmModel model = seedInstanceModel("binding-bad");
        model.setEnabled(false);
        llmModelRepository.save(model);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), "PRACTICE_DETECTION")
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", model.getId(), "enabled", true))
            .exchange()
            .expectStatus()
            .isBadRequest();
    }

    @Test
    @DisplayName("the endpoints require authentication")
    void anonymousIsRejected() {
        // The listing's own gate: SecurityConfig permits anonymous GET under a workspace slug, and the
        // member cases below only exercise PUT and DELETE — so @RequireAtLeastWorkspaceAdmin on
        // listAgents is pinned here and nowhere else.
        //
        // Against a REAL workspace: an unknown slug 404s during resolution, which would pass this
        // assertion without ever reaching the authentication check.
        User owner = persistUser("binding-anon-owner");
        Workspace workspace = createWorkspace(
            "binding-anon",
            "Binding Workspace",
            "binding-anon-org",
            AccountType.ORG,
            owner
        );

        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", workspace.getWorkspaceSlug())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithMentorUser
    @DisplayName("a plain member cannot configure an agent")
    void aWorkspaceMemberCannotPutABinding() {
        Workspace workspace = setupWorkspace("binding-member");
        // Login must match @WithMentorUser's default "mentor" principal so the membership resolver
        // finds this exact row.
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);

        webTestClient
            .put()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), AgentPurpose.PRACTICE_DETECTION)
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(Map.of("instanceModelId", seedInstanceModel("binding-member-model").getId(), "enabled", true))
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithMentorUser
    @DisplayName("a plain member cannot turn an agent off")
    void aWorkspaceMemberCannotDeleteABinding() {
        Workspace workspace = setupWorkspace("binding-member-del");
        User member = persistUser("mentor");
        ensureWorkspaceMembership(workspace, member, WorkspaceRole.MEMBER);

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agents/{purpose}", workspace.getWorkspaceSlug(), AgentPurpose.PRACTICE_DETECTION)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();
    }

    @Test
    @WithMentorUser
    @DisplayName("an admin of another workspace cannot configure this workspace's agents")
    void aWorkspaceAdminWithNoMembershipHereIsForbidden() {
        // A genuine (non-superadmin) workspace ADMIN. @WithAdminUser would carry the instance-wide
        // app_admin elevation and pass this call for the wrong reason.
        User admin = persistUser("mentor");
        Workspace own = createWorkspace("binding-own-ws", "Own", "binding-own-org", AccountType.ORG, admin);
        ensureWorkspaceMembership(own, admin, WorkspaceRole.ADMIN);

        Workspace other = createWorkspace(
            "binding-other-ws",
            "Other",
            "binding-other-org",
            AccountType.ORG,
            persistUser("binding-other-owner")
        );

        webTestClient
            .delete()
            .uri("/workspaces/{slug}/agents/{purpose}", other.getWorkspaceSlug(), AgentPurpose.PRACTICE_DETECTION)
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isForbidden();

        // Sanity: the same principal IS admitted in the workspace it actually administers.
        webTestClient
            .get()
            .uri("/workspaces/{slug}/agents", own.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .exchange()
            .expectStatus()
            .isOk();
    }
}

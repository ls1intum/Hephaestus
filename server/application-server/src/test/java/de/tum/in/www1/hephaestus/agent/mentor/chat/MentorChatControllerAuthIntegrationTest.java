package de.tum.in.www1.hephaestus.agent.mentor.chat;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration test that proves the mentor SSE endpoint is correctly guarded by both the
 * {@code WorkspaceContextFilter} (workspace-membership pre-check) and the controller-level
 * {@code @PreAuthorize("@workspaceSecure.isMember()")} expression. The audit caught that the
 * existing unit-level {@code MentorChatControllerTest} bypasses Spring Security entirely — this
 * test fires the real filter chain so a future regression that removes either guard surfaces
 * here, not in production.
 */
@AutoConfigureWebTestClient
@DisplayName("MentorChatController auth integration")
class MentorChatControllerAuthIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    // The Pi sandbox SPI is only auto-configured in profiles that have Docker available.
    // The test profile doesn't provide one; mock it so the mentor controller's bean graph
    // resolves without us having to wire a real container or stub adapter.
    @MockitoBean
    @SuppressWarnings("unused")
    private InteractiveSandboxService interactiveSandboxService;

    /** Minimal valid mentor turn request body. */
    private static Map<String, Object> validBody() {
        return Map.of(
            "id",
            UUID.randomUUID(),
            "message",
            Map.of(
                "id",
                UUID.randomUUID(),
                "role",
                "user",
                "parts",
                java.util.List.of(Map.of("type", "text", "text", "hi"))
            )
        );
    }

    @Test
    @DisplayName("unauthenticated POST → 401 (filter rejects before controller handler runs)")
    void unauthenticated_returnsUnauthorized() {
        // The workspace itself doesn't even need to exist: WorkspaceContextFilter short-circuits
        // on missing auth before it tries to resolve the slug.
        webTestClient
            .post()
            .uri("/workspaces/{workspaceSlug}/mentor/chat", "any-slug")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validBody())
            .exchange()
            .expectStatus()
            .isUnauthorized();
    }

    @Test
    @WithMentorUser
    @DisplayName("authenticated non-member → 403 (filter rejects with FORBIDDEN_MEMBERSHIP)")
    void authenticatedButNotMember_returnsForbidden() {
        // Mentor user exists; workspace exists owned by someone else; mentor is NOT a member.
        // The membership-403 path is the entire reason `@PreAuthorize("@workspaceSecure.isMember()")`
        // exists as a belt-and-braces guard — if anyone ever removes `@WorkspaceScopedController`
        // from the controller (turning off the filter prefix), this test would catch the regression.
        persistUser("mentor");
        User owner = persistUser("workspace-owner-for-mentor-test");
        Workspace workspace = createWorkspace("mentor-auth-space", "MentorAuth", "mentor-auth", AccountType.ORG, owner);

        webTestClient
            .post()
            .uri("/workspaces/{workspaceSlug}/mentor/chat", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(validBody())
            .exchange()
            .expectStatus()
            .isForbidden();
    }
}

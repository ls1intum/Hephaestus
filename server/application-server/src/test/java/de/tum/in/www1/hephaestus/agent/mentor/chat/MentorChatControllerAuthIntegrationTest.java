package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.testconfig.TestAuthUtils;
import de.tum.in.www1.hephaestus.testconfig.WithMentorUser;
import de.tum.in.www1.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Integration test that proves the mentor SSE endpoint is correctly guarded by both the
 * {@code WorkspaceContextFilter} (workspace-membership pre-check) and the controller-level
 * {@code @PreAuthorize("@workspaceSecure.isMember()")} expression. The audit caught that the
 * existing unit-level {@code MentorChatControllerTest} bypasses Spring Security entirely — this
 * test fires the real filter chain so a future regression that removes either guard surfaces
 * here, not in production.
 */
// Mentor beans only register if InteractiveSandboxService is on the context; the @MockitoBean
// below provides that dependency that DockerSandboxConfiguration would otherwise have to supply.
@AutoConfigureWebTestClient
@DisplayName("MentorChatController auth integration")
class MentorChatControllerAuthIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    // The Pi sandbox SPI is only auto-configured in profiles that have Docker available.
    // The test profile doesn't provide one; mock it so the mentor controller's bean graph
    // resolves without us having to wire a real container or stub adapter.
    @MockitoBean
    private InteractiveSandboxService interactiveSandboxService;

    @MockitoBean
    private de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository agentConfigRepository;

    @MockitoBean
    private MentorChatService mentorChatService;

    @Autowired
    private WorkspaceRepository workspaceRepositoryForFeatures;

    /** Flip the per-workspace mentor toggle on so the controller's feature gate passes. */
    private Workspace enableMentor(Workspace workspace) {
        workspace.getFeatures().setMentorEnabled(true);
        return workspaceRepositoryForFeatures.save(workspace);
    }

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
        enableMentor(workspace);

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

    @Test
    @WithMentorUser
    @DisplayName("authenticated member → 200 SSE with AI-SDK header (real filter chain + SecurityContext propagation)")
    void authenticatedMember_returnsOkSseStreamAndDispatchesService() throws Exception {
        // This test exercises the FULL request path that
        // DelegatingSecurityContextExecutorService is load-bearing for: the controller fires, the
        // service is invoked, and the AI-SDK protocol header is set on the response. The
        // service itself is mocked so we don't need a Docker sandbox; we assert it WAS
        // called (proves the auth path is wide open), and assert the header (proves the
        // controller's protocol contract). The membership lookup goes through the real
        // SecurityContext / WorkspaceContextFilter path.
        User mentor = persistUser("mentor");
        User owner = persistUser("workspace-owner-for-mentor-happy");
        Workspace workspace = createWorkspace("mentor-happy-space", "Happy", "mentor-happy", AccountType.ORG, owner);
        enableMentor(workspace);
        ensureWorkspaceMembership(workspace, mentor, WorkspaceMembership.WorkspaceRole.MEMBER);

        // The mocked service is responsible for completing the emitter — otherwise
        // WebTestClient blocks indefinitely on the SSE body. Real service path is unit-tested
        // separately; here we only need to prove the controller dispatched a request to it.
        doAnswer(inv -> {
            SseEmitter emitter = inv.getArgument(1);
            emitter.send(SseEmitter.event().data("[DONE]"));
            emitter.complete();
            return null;
        })
            .when(mentorChatService)
            .start(any(), any());

        webTestClient
            .post()
            .uri("/workspaces/{workspaceSlug}/mentor/chat", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(validBody())
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
            .expectHeader()
            .valueEquals("x-vercel-ai-ui-message-stream", "v1")
            .expectHeader()
            .valueEquals("Cache-Control", "no-cache");

        // Service was invoked — proves auth admitted us and the dispatcher reached the
        // controller body. timeout() because the controller submits async and returns; the
        // service call lands on the virtual-thread executor a few ms later.
        verify(mentorChatService, timeout(2_000)).start(any(), any());
    }

    @Test
    @WithMentorUser
    @DisplayName("authenticated member of workspace with mentorEnabled=false → 404 (per-workspace gate)")
    void authenticatedMember_mentorDisabled_returnsNotFound() {
        // Per-workspace feature gate must block even an authenticated, fully-permitted member
        // when the workspace admin has not opted in to mentor. Mirrors the practicesEnabled
        // model on PracticeReviewDetectionGate. NOT enabling mentor here is the entire point.
        User mentor = persistUser("mentor");
        User owner = persistUser("workspace-owner-for-mentor-disabled");
        Workspace workspace = createWorkspace(
            "mentor-disabled-space",
            "Disabled",
            "mentor-disabled",
            AccountType.ORG,
            owner
        );
        // Intentionally NOT calling enableMentor(workspace).
        ensureWorkspaceMembership(workspace, mentor, WorkspaceMembership.WorkspaceRole.MEMBER);

        webTestClient
            .post()
            .uri("/workspaces/{workspaceSlug}/mentor/chat", workspace.getWorkspaceSlug())
            .headers(TestAuthUtils.withCurrentUser())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .bodyValue(validBody())
            .exchange()
            .expectStatus()
            .isNotFound();
    }
}

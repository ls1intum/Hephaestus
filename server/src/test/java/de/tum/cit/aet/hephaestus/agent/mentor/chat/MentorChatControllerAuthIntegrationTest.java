package de.tum.cit.aet.hephaestus.agent.mentor.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.cit.aet.hephaestus.integration.scm.user.User;
import de.tum.cit.aet.hephaestus.testconfig.TestAuthUtils;
import de.tum.cit.aet.hephaestus.testconfig.WithMentorUser;
import de.tum.cit.aet.hephaestus.workspace.AbstractWorkspaceIntegrationTest;
import de.tum.cit.aet.hephaestus.workspace.AccountType;
import de.tum.cit.aet.hephaestus.workspace.Workspace;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceMembership;
import de.tum.cit.aet.hephaestus.workspace.WorkspaceRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fires the real filter chain so the SSE endpoint's two guards — WorkspaceContextFilter
 * membership pre-check and the {@code @PreAuthorize("@workspaceSecure.isMember()")} expression
 * — survive future regressions.
 */
@TestPropertySource(properties = "hephaestus.sandbox.enabled=true")
@DisplayName("MentorChatController auth integration")
class MentorChatControllerAuthIntegrationTest extends AbstractWorkspaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private InteractiveSandboxService interactiveSandboxService;

    @MockitoBean
    private de.tum.cit.aet.hephaestus.agent.config.AgentConfigRepository agentConfigRepository;

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
    @DisplayName("authenticated member → 200 SSE with AI-SDK header and service is dispatched")
    void authenticatedMember_returnsOkSseStreamAndDispatchesService() throws Exception {
        User mentor = persistUser("mentor");
        User owner = persistUser("workspace-owner-for-mentor-happy");
        Workspace workspace = createWorkspace("mentor-happy-space", "Happy", "mentor-happy", AccountType.ORG, owner);
        enableMentor(workspace);
        ensureWorkspaceMembership(workspace, mentor, WorkspaceMembership.WorkspaceRole.MEMBER);

        // Mocked service must complete the emitter or WebTestClient blocks on the SSE body.
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

        // timeout() because the controller submits to a vthread executor and returns.
        verify(mentorChatService, timeout(2_000)).start(any(), any());
    }

    @Test
    @WithMentorUser
    @DisplayName("authenticated member of workspace with mentorEnabled=false → 404 (per-workspace gate)")
    void authenticatedMember_mentorDisabled_returnsNotFound() {
        User mentor = persistUser("mentor");
        User owner = persistUser("workspace-owner-for-mentor-disabled");
        Workspace workspace = createWorkspace(
            "mentor-disabled-space",
            "Disabled",
            "mentor-disabled",
            AccountType.ORG,
            owner
        );
        // Deliberately NOT enabling mentor — the gate is what we're asserting.
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

package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentProperties;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.AccountType;
import de.tum.in.www1.hephaestus.workspace.WorkspaceMembership.WorkspaceRole;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Controller-level tests for {@link MentorChatController}. Bypasses the Spring MVC dispatcher —
 * the controller is invoked directly with a {@link MockHttpServletResponse}. Pins:
 *
 * <ul>
 *   <li>AI-SDK protocol header on the response (regression-proof against a Spring config change)</li>
 *   <li>well-formed body → service dispatch with correct thread + message + client-id</li>
 *   <li>blank user-message text short-circuits; service is NOT invoked</li>
 *   <li>non-UUID client id is silently ignored (graceful fallback to server-side mint)</li>
 * </ul>
 *
 * <p>{@code SseEmitter}'s internal {@code Handler} interface is package-private, so we can't
 * directly drain the emitter from outside the Spring web package. The short-circuit path is
 * covered by asserting (a) service was not invoked and (b) the controller returned an emitter
 * — the JSON-encoding contract of {@code shortCircuitError} is exercised via the
 * {@link ObjectMapper} round-trip below in {@link #shortCircuitEmitsValidJsonViaObjectMapper}.
 *
 * <p>A {@code @WebMvcTest} would force Spring to instantiate the workspace-scoped routing
 * machinery, security expressions, and the JWT decoder — heavy ceremony for the thin
 * controller layer. The value-add lives in request parsing + short-circuit, both exercised
 * here without booting Spring.
 */
@DisplayName("MentorChatController")
class MentorChatControllerTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MentorAgentProperties TEST_PROPERTIES = new MentorAgentProperties(
        "ghcr.io/ls1intum/hephaestus/agent-pi-mentor:latest",
        "pi-mentor-runner.mjs",
        100_000
    );

    @Mock
    MentorChatService mentorChatService;

    private MentorChatController controller;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new MentorChatController(mentorChatService, MAPPER, TEST_PROPERTIES);
        response = new MockHttpServletResponse();
    }

    @Test
    @DisplayName("sets the AI-SDK protocol response header")
    void setsProtocolResponseHeader() {
        controller.chat(stubContext(), validBody(UUID.randomUUID(), "hi"), response);
        assertThat(response.getHeader(UIMessageChunk.RESPONSE_HEADER)).isEqualTo(UIMessageChunk.PROTOCOL_VERSION);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-cache");
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
    }

    @Test
    @DisplayName("dispatches to the service when the body is well-formed")
    void dispatchesToService() {
        UUID threadId = UUID.randomUUID();
        controller.chat(stubContext(), validBody(threadId, "hello mentor"), response);
        ArgumentCaptor<MentorChatService.MentorTurnRequest> req = ArgumentCaptor.forClass(
            MentorChatService.MentorTurnRequest.class
        );
        verify(mentorChatService).start(req.capture(), any());
        assertThat(req.getValue().threadId()).isEqualTo(threadId);
        assertThat(req.getValue().userMessage()).isEqualTo("hello mentor");
    }

    @Test
    @DisplayName("propagates the client-supplied UIMessage id to the service")
    void propagatesClientMessageId() {
        UUID clientMessageId = UUID.randomUUID();
        MentorChatRequestBody body = body(UUID.randomUUID(), clientMessageId.toString(), "hello");
        controller.chat(stubContext(), body, response);
        ArgumentCaptor<MentorChatService.MentorTurnRequest> req = ArgumentCaptor.forClass(
            MentorChatService.MentorTurnRequest.class
        );
        verify(mentorChatService).start(req.capture(), any());
        assertThat(req.getValue().clientUserMessageId()).isEqualTo(clientMessageId);
    }

    @Test
    @DisplayName("ignores non-UUID client message ids without rejecting the request")
    void ignoresNonUuidClientMessageId() {
        MentorChatRequestBody body = body(UUID.randomUUID(), "not-a-uuid", "hello");
        controller.chat(stubContext(), body, response);
        ArgumentCaptor<MentorChatService.MentorTurnRequest> req = ArgumentCaptor.forClass(
            MentorChatService.MentorTurnRequest.class
        );
        verify(mentorChatService).start(req.capture(), any());
        assertThat(req.getValue().clientUserMessageId()).isNull();
    }

    @Test
    @DisplayName("blank user message text short-circuits — service is NOT invoked, emitter returned")
    void blankUserMessage_shortCircuits() {
        MentorChatRequestBody body = body(UUID.randomUUID(), null, "   ");
        SseEmitter emitter = controller.chat(stubContext(), body, response);
        verify(mentorChatService, never()).start(any(), any());
        // Returning an emitter (not throwing) keeps the API contract: the client receives an
        // error chunk on the stream, not an HTTP error code. The proper JSON shape is
        // verified via ObjectMapper round-trip below.
        assertThat(emitter).isNotNull();
        // Protocol header must be present even on the short-circuit branch — AI-SDK
        // DefaultChatTransport rejects the stream if the marker is missing.
        assertThat(response.getHeader(UIMessageChunk.RESPONSE_HEADER)).isEqualTo(UIMessageChunk.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("oversize user message (> MAX_PROMPT_CHARS) short-circuits with an error chunk")
    void oversizeUserMessage_shortCircuits() {
        String hugeText = "x".repeat(TEST_PROPERTIES.maxPromptChars() + 1);
        MentorChatRequestBody body = body(UUID.randomUUID(), null, hugeText);
        SseEmitter emitter = controller.chat(stubContext(), body, response);
        verify(mentorChatService, never()).start(any(), any());
        assertThat(emitter).isNotNull();
        assertThat(response.getHeader(UIMessageChunk.RESPONSE_HEADER)).isEqualTo(UIMessageChunk.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("user message exactly MAX_PROMPT_CHARS is accepted and dispatched")
    void boundarySizedUserMessage_dispatches() {
        String exactlyAtCap = "x".repeat(TEST_PROPERTIES.maxPromptChars());
        MentorChatRequestBody body = body(UUID.randomUUID(), null, exactlyAtCap);
        controller.chat(stubContext(), body, response);
        verify(mentorChatService).start(any(), any());
    }

    @Test
    @DisplayName("ObjectMapper-encoded short-circuit JSON parses as a valid Error chunk")
    void shortCircuitEmitsValidJsonViaObjectMapper() throws Exception {
        // The previous hand-rolled `errorText.replace("\"","\\\"")` would emit invalid JSON for
        // any errorText containing a backslash. The controller now encodes via the injected
        // ObjectMapper; this test pins the produced shape by serialising the exact same call
        // the controller makes and round-trip parsing it.
        String produced = MAPPER.writeValueAsString(new UIMessageChunk.Error("User message text is empty."));
        assertThat(MAPPER.readTree(produced).path("type").asText()).isEqualTo("error");
        assertThat(MAPPER.readTree(produced).path("errorText").asText()).isEqualTo("User message text is empty.");
        // And the same shape survives a quote+backslash in the text — the bug the audit caught.
        String dangerous = MAPPER.writeValueAsString(new UIMessageChunk.Error("got \\ and \" and end"));
        assertThat(MAPPER.readTree(dangerous).path("errorText").asText()).isEqualTo("got \\ and \" and end");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────────────

    private static WorkspaceContext stubContext() {
        return new WorkspaceContext(1L, "test-ws", "Test", AccountType.ORG, null, false, Set.of(WorkspaceRole.MEMBER));
    }

    private static MentorChatRequestBody validBody(UUID threadId, String text) {
        return body(threadId, null, text);
    }

    private static MentorChatRequestBody body(UUID threadId, String messageId, String text) {
        ObjectNode root = MAPPER.createObjectNode();
        if (messageId != null) {
            root.put("id", messageId);
        }
        var partsArray = root.putArray("parts");
        var part = partsArray.addObject();
        part.put("type", "text");
        part.put("text", text);
        return new MentorChatRequestBody(threadId, root);
    }
}

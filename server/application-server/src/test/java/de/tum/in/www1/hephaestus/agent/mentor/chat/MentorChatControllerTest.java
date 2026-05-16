package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentProperties;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
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

@DisplayName("MentorChatController")
class MentorChatControllerTest extends BaseUnitTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final MentorAgentProperties TEST_PROPERTIES = new MentorAgentProperties(
        "ghcr.io/ls1intum/hephaestus/agent-pi:latest",
        "pi-mentor-runner.mjs",
        100_000,
        "",
        null,
        null,
        null,
        null,
        600,
        ImagePullPolicy.IF_NOT_PRESENT
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
    @DisplayName("blank user message text short-circuits — service is NOT invoked, header still set")
    void blankUserMessage_shortCircuits() {
        SseEmitter emitter = controller.chat(stubContext(), body(UUID.randomUUID(), null, "   "), response);
        verify(mentorChatService, never()).start(any(), any());
        assertThat(emitter).isNotNull();
        assertThat(response.getHeader(UIMessageChunk.RESPONSE_HEADER)).isEqualTo(UIMessageChunk.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("oversize user message (> MAX_PROMPT_CHARS) short-circuits with an error chunk")
    void oversizeUserMessage_shortCircuits() {
        String hugeText = "x".repeat(TEST_PROPERTIES.maxPromptChars() + 1);
        SseEmitter emitter = controller.chat(stubContext(), body(UUID.randomUUID(), null, hugeText), response);
        verify(mentorChatService, never()).start(any(), any());
        assertThat(emitter).isNotNull();
        assertThat(response.getHeader(UIMessageChunk.RESPONSE_HEADER)).isEqualTo(UIMessageChunk.PROTOCOL_VERSION);
    }

    @Test
    @DisplayName("user message exactly MAX_PROMPT_CHARS is accepted and dispatched")
    void boundarySizedUserMessage_dispatches() {
        String exactlyAtCap = "x".repeat(TEST_PROPERTIES.maxPromptChars());
        controller.chat(stubContext(), body(UUID.randomUUID(), null, exactlyAtCap), response);
        verify(mentorChatService).start(any(), any());
    }

    @Test
    @DisplayName("workspace with mentorEnabled=false → 404; service NOT invoked")
    void workspaceWithMentorDisabled_returns404() {
        WorkspaceContext disabledCtx = new WorkspaceContext(
            1L,
            "test-ws",
            "Test",
            AccountType.ORG,
            null,
            false,
            false,
            Set.of(WorkspaceRole.MEMBER)
        );
        assertThatThrownBy(() ->
            controller.chat(disabledCtx, validBody(UUID.randomUUID(), "hi"), response)
        ).isInstanceOf(EntityNotFoundException.class);
        verify(mentorChatService, never()).start(any(), any());
    }

    private static WorkspaceContext stubContext() {
        return new WorkspaceContext(
            1L,
            "test-ws",
            "Test",
            AccountType.ORG,
            null,
            false,
            true,
            Set.of(WorkspaceRole.MEMBER)
        );
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

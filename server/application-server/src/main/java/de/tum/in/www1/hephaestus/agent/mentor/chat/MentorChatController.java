package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceContext;
import de.tum.in.www1.hephaestus.workspace.context.WorkspaceScopedController;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE entry point for mentor chat. Sets the AI-SDK protocol header and submits a turn to the
 * mentor virtual-thread executor; the orchestration lives in {@link MentorChatService}.
 */
@WorkspaceScopedController
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true")
@RequestMapping("/mentor/chat")
@Tag(name = "Mentor Chat", description = "Mentor chat SSE stream")
@RequiredArgsConstructor
@Hidden
public class MentorChatController {

    private static final Logger log = LoggerFactory.getLogger(MentorChatController.class);
    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(10).toMillis();

    private final MentorChatService mentorChatService;
    private final ObjectMapper objectMapperBean;

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Send one mentor-chat turn; stream the response as AI SDK UIMessage chunks")
    @PreAuthorize("@workspaceSecure.isMember()")
    public SseEmitter chat(
        WorkspaceContext workspaceContext,
        @RequestBody MentorChatRequestBody body,
        HttpServletResponse response
    ) {
        response.setHeader(UIMessageChunk.RESPONSE_HEADER, UIMessageChunk.PROTOCOL_VERSION);
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");

        String userMessage = extractUserMessage(body.message());
        if (userMessage == null || userMessage.isBlank()) {
            SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
            shortCircuitError(emitter, "User message text is empty.");
            return emitter;
        }

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        UUID threadId = body.id() != null ? body.id() : UUID.randomUUID();
        UUID clientUserMessageId = extractMessageId(body.message());
        MentorChatService.MentorTurnRequest serviceRequest = new MentorChatService.MentorTurnRequest(
            workspaceContext.id(),
            threadId,
            userMessage,
            clientUserMessageId
        );
        mentorChatService.start(serviceRequest, emitter);
        return emitter;
    }

    private static String extractUserMessage(JsonNode message) {
        if (message == null || !message.isObject() || !message.has("parts") || !message.get("parts").isArray()) {
            return null;
        }
        for (JsonNode part : message.get("parts")) {
            if (part.isObject() && "text".equals(textOrNull(part, "type"))) {
                String text = textOrNull(part, "text");
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    /**
     * Extract the client-supplied UIMessage id. The webapp's AI SDK transport mints a UUID per
     * outbound message; persisting it is required for vote / regenerate / refresh reconciliation
     * (otherwise the client's optimistic row never resolves to the server-side id). Null and
     * non-UUID values fall back to a server-side mint inside the persistence layer.
     */
    private static UUID extractMessageId(JsonNode message) {
        if (message == null || !message.isObject()) return null;
        String raw = textOrNull(message, "id");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            log.debug("Discarding non-UUID client message id '{}'", raw);
            return null;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        return node.has(field) && node.get(field).isTextual() ? node.get(field).asText() : null;
    }

    /**
     * Emit a single {@code error} chunk and close the emitter. Uses {@link ObjectMapper}
     * to encode so a backslash or quote inside {@code errorText} survives unescaped — the
     * earlier hand-rolled JSON concatenation only escaped naive double quotes.
     */
    private void shortCircuitError(SseEmitter emitter, String errorText) {
        try {
            String json = objectMapperBean.writeValueAsString(new UIMessageChunk.Error(errorText));
            emitter.send(SseEmitter.event().data(json));
        } catch (Exception ignored) {
            log.debug("Short-circuit emitter send failed; client disconnected");
        }
        emitter.complete();
    }
}

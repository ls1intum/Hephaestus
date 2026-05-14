package de.tum.in.www1.hephaestus.agent.mentor.chat.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.mentor.chat.wire.UIMessageChunk;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Translates Pi {@code AgentSessionEvent} JSON into AI SDK {@link UIMessageChunk}s. Stateful
 * per turn; the caller threads a {@link TranslatorState} through every call. Unknown event
 * types yield an empty list (do not throw — one bad frame must not poison the turn).
 */
@Component
public class MentorEventTranslator {

    private static final Logger log = LoggerFactory.getLogger(MentorEventTranslator.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /**
     * Translate a single Pi event into zero or more UI chunks. Idempotency / replay handling is
     * out of scope here — the runner is responsible for not double-emitting; the translator
     * trusts the stream.
     */
    public List<UIMessageChunk> translate(JsonNode piEvent, TranslatorState state) {
        if (piEvent == null || !piEvent.isObject() || !piEvent.has("type")) {
            log.debug("Skipping malformed Pi event (missing type): {}", piEvent);
            return List.of();
        }
        String type = piEvent.get("type").asText();
        return switch (type) {
            case "message_start" -> handleMessageStart(piEvent, state);
            case "message_update" -> handleMessageUpdate(piEvent, state);
            case "message_end" -> handleMessageEnd(piEvent, state);
            case "tool_execution_start" -> handleToolStart(piEvent, state);
            case "tool_execution_end" -> handleToolEnd(piEvent, state);
            case "turn_end" -> handleTurnEnd(state);
            case "agent_end" -> handleAgentEnd(piEvent, state);
            case "link_finding" -> handleLinkFinding(piEvent, state);
            case "pi_error", "turn_watchdog_fired" -> handleError(piEvent);
            case "runner_ready" -> List.of(); // Controller consumes this directly via the subscription.
            default -> {
                log.debug("Unhandled Pi event type '{}' — dropping", type);
                yield List.of();
            }
        };
    }

    // ─── message_end → no chunks, but captures the authoritative usage snapshot ───────────

    private List<UIMessageChunk> handleMessageEnd(JsonNode event, TranslatorState state) {
        // Per pi-coding-agent dist/core/extensions/types.d.ts MessageEndEvent.message: AgentMessage.
        // For assistant messages the final, authoritative `usage` + `model` live here. For tool
        // result and user messages this branch is a no-op. We don't emit UIMessageChunks; the
        // turn-level Finish chunk on agent_end carries the metadata to the client.
        capturePartialUsage(event.path("message"), state);
        return List.of();
    }

    // ─── message_start / Start + StartStep ────────────────────────────────────────────────

    private List<UIMessageChunk> handleMessageStart(JsonNode event, TranslatorState state) {
        // Pi shape per pi-coding-agent/dist/core/extensions/types.d.ts MessageStartEvent:
        //   {type:"message_start", message: AgentMessage}
        // The role lives on `message.role`. Top-level `role` is the older stub-shape; we keep
        // it as a fallback for backwards compat with the protocol-only test fixtures.
        String role = optionalString(event.path("message"), "role");
        if (role == null) role = optionalString(event, "role");
        if (role != null && !"assistant".equals(role)) {
            return List.of();
        }
        // Capture model + any opening usage snapshot on the assistant message header.
        capturePartialUsage(event.path("message"), state);
        if (state.isStarted()) {
            // Defensive: a runner glitch that double-emits message_start. We already opened the
            // top-level Start chunk; re-emitting it would confuse useChat's reconciliation.
            return List.of();
        }
        state.markStarted();
        state.incrementStep();
        List<UIMessageChunk> out = new ArrayList<>(2);
        out.add(new UIMessageChunk.Start(state.assistantMessageId(), null));
        out.add(new UIMessageChunk.StartStep());
        return out;
    }

    // ─── message_update: text_delta + thinking_delta ──────────────────────────────────────

    private List<UIMessageChunk> handleMessageUpdate(JsonNode event, TranslatorState state) {
        // Pi SDK canonical shape (verified against @earendil-works/pi-ai dist/types.d.ts AssistantMessageEvent
        // union and @earendil-works/pi-coding-agent dist/core/extensions/types.d.ts MessageUpdateEvent):
        //   {type: "message_update", message: AgentMessage,
        //    assistantMessageEvent: {type: "text_delta"|"thinking_delta"|..., contentIndex: number, delta: string, partial: AssistantMessage}}
        //
        // The earlier shape (snake_case `delta_type`/`delta` at the top level) is what the
        // PROTOCOL_ONLY stub used to emit and what some forks of the SDK exposed; we accept it as
        // a fallback so the protocol-only test suite + the runner's own synthetic `pi_error`/
        // watchdog events keep working.
        JsonNode ame = event.path("assistantMessageEvent");
        if (ame.isObject() && ame.has("type")) {
            return handleAssistantMessageEvent(ame, event, state);
        }
        // Snake-case fallback (legacy stub + synthetic events).
        JsonNode deltaWrapper = event.has("update") && event.get("update").isObject() ? event.get("update") : event;
        String deltaType = optionalString(deltaWrapper, "delta_type");
        if (deltaType == null) {
            deltaType = optionalString(event, "delta_type");
        }
        String deltaText = optionalString(deltaWrapper, "delta");
        if (deltaText == null) {
            deltaText = optionalString(event, "delta");
        }
        if (deltaType == null || deltaText == null) {
            log.debug("message_update missing delta_type or delta — skipping: {}", event);
            return List.of();
        }
        String blockId = optionalString(deltaWrapper, "id");
        if (blockId == null) {
            blockId = optionalString(event, "id");
        }
        return mapDelta(deltaType, deltaText, blockId, state);
    }

    /**
     * Translate the inner {@code assistantMessageEvent} payload Pi attaches to every
     * {@code message_update}. {@code contentIndex} is a stable per-message integer that lets us
     * keep concurrent text + reasoning blocks separate; we derive a stable {@code text-<n>} /
     * {@code reasoning-<n>} block id from it so subsequent deltas reconcile to the same
     * {@link UIMessageChunk.TextStart} on the AI SDK side. Usage is captured opportunistically:
     * {@code partial.usage} accumulates per chunk on most providers (per
     * {@code AssistantMessageEvent.partial: AssistantMessage}). It is also re-emitted as a final
     * snapshot on {@code message_end.message.usage}, which is the authoritative source.
     */
    private List<UIMessageChunk> handleAssistantMessageEvent(JsonNode ame, JsonNode parent, TranslatorState state) {
        String innerType = ame.get("type").asText();
        // Best-effort usage + model capture from `partial.usage` / `partial.model`. message_end
        // overwrites with the final snapshot. agent_end has no usage on the event itself.
        capturePartialUsage(ame.path("partial"), state);
        capturePartialUsage(parent.path("message"), state);
        String blockId = blockIdFor(ame, innerType);
        return switch (innerType) {
            case "text_delta" -> {
                String delta = optionalString(ame, "delta");
                yield delta == null ? List.of() : textDelta(blockId, delta, state);
            }
            case "thinking_delta" -> {
                String delta = optionalString(ame, "delta");
                yield delta == null ? List.of() : reasoningDelta(blockId, delta, state);
            }
            // These are fine-grained lifecycle events Pi emits inside the message stream.
            // The AI SDK already models TextStart/TextEnd via the lazy lifecycle we open on
            // first delta. Tool calls surface via the top-level `tool_execution_*` events,
            // not these inner ones. Dropping here is intentional, not a bug.
            case
                "text_start",
                "text_end",
                "thinking_start",
                "thinking_end",
                "toolcall_start",
                "toolcall_delta",
                "toolcall_end",
                "start",
                "done",
                "error" -> List.of();
            default -> {
                log.debug("Unknown assistantMessageEvent.type '{}' — dropping", innerType);
                yield List.of();
            }
        };
    }

    private List<UIMessageChunk> mapDelta(
        String deltaType,
        String deltaText,
        @Nullable String blockId,
        TranslatorState state
    ) {
        return switch (deltaType) {
            case "text_delta", "text" -> textDelta(blockId, deltaText, state);
            case "thinking_delta", "reasoning_delta", "thinking" -> reasoningDelta(blockId, deltaText, state);
            default -> {
                log.debug("Unknown message_update delta_type '{}' — dropping", deltaType);
                yield List.of();
            }
        };
    }

    private static String blockIdFor(JsonNode ame, String innerType) {
        // Pi's contentIndex is the per-message position of the content block. Map it to a stable
        // block id so concurrent deltas merge into the same TextStart on the AI SDK side. We
        // namespace by inner-type so text-0 and reasoning-0 never collide.
        JsonNode idx = ame.path("contentIndex");
        long index = idx.isIntegralNumber() ? idx.asLong() : 0L;
        String prefix = innerType.startsWith("thinking") ? "reasoning-" : "text-";
        return prefix + index;
    }

    private static void capturePartialUsage(JsonNode messageNode, TranslatorState state) {
        if (messageNode == null || messageNode.isMissingNode() || !messageNode.isObject()) {
            return;
        }
        if (messageNode.has("model") && messageNode.get("model").isTextual()) {
            state.observeModel(messageNode.get("model").asText());
        }
        JsonNode usage = messageNode.path("usage");
        if (usage.isObject()) {
            state.observeUsage(usage);
        }
    }

    private List<UIMessageChunk> textDelta(@Nullable String blockId, String deltaText, TranslatorState state) {
        List<UIMessageChunk> out = new ArrayList<>(2);
        if (state.activeTextId() == null) {
            String id = blockId != null ? blockId : "text-" + UUID.randomUUID();
            state.openTextBlock(id);
            out.add(new UIMessageChunk.TextStart(id));
        }
        state.appendText(deltaText);
        out.add(new UIMessageChunk.TextDelta(state.activeTextId(), deltaText));
        return out;
    }

    private List<UIMessageChunk> reasoningDelta(@Nullable String blockId, String deltaText, TranslatorState state) {
        List<UIMessageChunk> out = new ArrayList<>(2);
        if (state.activeReasoningId() == null) {
            String id = blockId != null ? blockId : "reasoning-" + UUID.randomUUID();
            state.openReasoningBlock(id);
            out.add(new UIMessageChunk.ReasoningStart(id));
        }
        state.appendReasoning(deltaText);
        out.add(new UIMessageChunk.ReasoningDelta(state.activeReasoningId(), deltaText));
        return out;
    }

    // ─── tool_execution_start / end ───────────────────────────────────────────────────────

    private List<UIMessageChunk> handleToolStart(JsonNode event, TranslatorState state) {
        // Pi shape per pi-coding-agent/dist/core/extensions/types.d.ts ToolExecutionStartEvent:
        //   {type:"tool_execution_start", toolCallId, toolName, args}
        // Snake-case fallback kept for the runner's stub events and forward-compat.
        String toolCallId = optionalString(event, "toolCallId");
        if (toolCallId == null) toolCallId = optionalString(event, "tool_call_id");
        if (toolCallId == null) toolCallId = optionalString(event, "id");
        String toolName = optionalString(event, "toolName");
        if (toolName == null) toolName = optionalString(event, "tool_name");
        if (toolName == null) toolName = optionalString(event, "name");
        if (toolCallId == null || toolName == null) {
            log.debug("tool_execution_start missing toolCallId or toolName — skipping: {}", event);
            return List.of();
        }
        JsonNode input = firstPresent(event, "args", "input", "arguments");
        state.recordToolCallStart(toolCallId, toolName, input);
        // Closing any open text block before the tool call mirrors what AI SDK does on the client
        // — tools render as discrete blocks; keeping text "open" across them confuses the UI.
        List<UIMessageChunk> out = closeOpenStreamingBlocks(state);
        out.add(new UIMessageChunk.ToolInputStart(toolCallId, toolName));
        out.add(
            new UIMessageChunk.ToolInputAvailable(toolCallId, toolName, input != null ? input : NODES.objectNode())
        );
        return out;
    }

    private List<UIMessageChunk> handleToolEnd(JsonNode event, TranslatorState state) {
        // Pi shape per ToolExecutionEndEvent:
        //   {type:"tool_execution_end", toolCallId, toolName, result, isError: boolean}
        // `isError` is the authoritative success/failure flag. Snake-case `error.message` is
        // accepted as a fallback for the runner's synthetic events.
        String toolCallId = optionalString(event, "toolCallId");
        if (toolCallId == null) toolCallId = optionalString(event, "tool_call_id");
        if (toolCallId == null) toolCallId = optionalString(event, "id");
        if (toolCallId == null) {
            log.debug("tool_execution_end missing toolCallId — skipping: {}", event);
            return List.of();
        }
        boolean isError;
        if (event.has("isError")) {
            isError = event.get("isError").asBoolean(false);
        } else {
            isError = event.has("error") && !event.get("error").isNull();
        }
        if (!isError) {
            JsonNode output = firstPresent(event, "result", "output");
            state.recordToolCallOutput(toolCallId, output);
            return List.of(
                new UIMessageChunk.ToolOutputAvailable(toolCallId, output != null ? output : NODES.nullNode())
            );
        }
        // Failure: prefer extracting a readable message from result.content[0].text (Pi tool
        // result shape) before falling back to legacy `error.message`.
        String errorText = extractToolErrorText(event);
        state.recordToolCallError(toolCallId, errorText);
        return List.of(new UIMessageChunk.ToolOutputError(toolCallId, errorText));
    }

    private static String extractToolErrorText(JsonNode event) {
        JsonNode result = event.path("result");
        if (result.isObject()) {
            JsonNode content = result.path("content");
            if (content.isArray() && !content.isEmpty()) {
                JsonNode first = content.get(0);
                if (first != null && first.isObject() && first.has("text")) {
                    return first.get("text").asText();
                }
            }
            if (result.has("message") && result.get("message").isTextual()) {
                return result.get("message").asText();
            }
        }
        JsonNode error = event.path("error");
        if (error.isObject()) {
            String m = optionalString(error, "message");
            if (m != null) return m;
            return error.toString();
        }
        if (error.isTextual()) return error.asText();
        return "tool execution failed";
    }

    @Nullable
    private static JsonNode firstPresent(JsonNode event, String... fields) {
        for (String f : fields) {
            if (event.has(f) && !event.get(f).isNull()) {
                return event.get(f);
            }
        }
        return null;
    }

    // ─── turn_end ─────────────────────────────────────────────────────────────────────────

    private List<UIMessageChunk> handleTurnEnd(TranslatorState state) {
        List<UIMessageChunk> out = closeOpenStreamingBlocks(state);
        state.decrementStep();
        out.add(new UIMessageChunk.FinishStep());
        return out;
    }

    private List<UIMessageChunk> closeOpenStreamingBlocks(TranslatorState state) {
        List<UIMessageChunk> out = new ArrayList<>(2);
        if (state.activeTextId() != null) {
            String id = state.activeTextId();
            state.closeTextBlock();
            out.add(new UIMessageChunk.TextEnd(id));
        }
        if (state.activeReasoningId() != null) {
            String id = state.activeReasoningId();
            state.closeReasoningBlock();
            out.add(new UIMessageChunk.ReasoningEnd(id));
        }
        return out;
    }

    // ─── agent_end → Finish ──────────────────────────────────────────────────────────────

    private List<UIMessageChunk> handleAgentEnd(JsonNode event, TranslatorState state) {
        // Pi shape per pi-coding-agent/dist/core/extensions/types.d.ts AgentEndEvent:
        //   {type:"agent_end", messages: AgentMessage[]}
        // Crucially, agent_end has NO top-level `usage`, `model`, or `stopReason` — those live
        // on each AssistantMessage (verified pi-ai/src/types.ts:277-287). We walk the last
        // assistant message in `messages` to harvest model+usage+stopReason as a last-resort
        // fallback if neither message_update nor message_end ran.
        String piStopReason = null;
        if (event.path("messages").isArray()) {
            for (JsonNode msg : event.get("messages")) {
                if (msg != null && msg.isObject() && "assistant".equals(optionalString(msg, "role"))) {
                    if (!state.hasObservedUsage()) {
                        capturePartialUsage(msg, state);
                    }
                    String reason = optionalString(msg, "stopReason");
                    if (reason != null) piStopReason = reason; // last assistant wins
                }
            }
        }
        if (piStopReason == null) {
            piStopReason = firstNonNull(optionalString(event, "stopReason"), optionalString(event, "finish_reason"));
        }
        List<UIMessageChunk> out = closeOpenStreamingBlocks(state);
        ObjectNode metadata = NODES.objectNode();
        JsonNode usage = state.observedUsage();
        if (usage != null) {
            metadata.set("usage", usage);
        }
        String model = state.observedModel();
        if (model != null) {
            metadata.put("model", model);
        }
        out.add(new UIMessageChunk.Finish(mapStopReason(piStopReason), metadata.isEmpty() ? null : metadata));
        return out;
    }

    /**
     * Map Pi's {@code StopReason} (pi-ai/src/types.ts:269 — {@code stop|length|toolUse|error|aborted})
     * to the AI SDK's {@code LanguageModelV2FinishReason} enum
     * (vercel/ai packages/provider/src/language-model/v2/language-model-v2-finish-reason.ts —
     * {@code stop|length|content-filter|tool-calls|error|other|unknown}). Strict client-side
     * validators reject raw Pi values, so the mapping must happen at this boundary.
     */
    @Nullable
    static String mapStopReason(@Nullable String piStopReason) {
        if (piStopReason == null) return null;
        return switch (piStopReason) {
            case "stop" -> "stop";
            case "length" -> "length";
            case "toolUse", "tool_use", "tool-calls" -> "tool-calls";
            case "error", "aborted" -> "error";
            default -> "unknown";
        };
    }

    @Nullable
    private static String firstNonNull(@Nullable String a, @Nullable String b) {
        return a != null ? a : b;
    }

    // ─── link_finding → DataFinding ──────────────────────────────────────────────────────

    private List<UIMessageChunk> handleLinkFinding(JsonNode event, TranslatorState state) {
        // Runner emits camelCase `findingId` (pi-mentor-runner.mjs defineLinkFindingTool); the
        // older snake_case form is accepted for back-compat.
        String findingIdStr = optionalString(event, "findingId");
        if (findingIdStr == null) {
            findingIdStr = optionalString(event, "finding_id");
        }
        if (findingIdStr == null) {
            log.debug("link_finding missing finding_id — skipping: {}", event);
            return List.of();
        }
        try {
            UUID findingId = UUID.fromString(findingIdStr);
            state.recordDataFinding(findingId);
            return List.of(UIMessageChunk.DataFinding.of(findingId));
        } catch (IllegalArgumentException e) {
            log.warn("link_finding has invalid UUID '{}' — dropping", findingIdStr);
            return List.of();
        }
    }

    // ─── pi_error / turn_watchdog_fired → Error ───────────────────────────────────────────

    private List<UIMessageChunk> handleError(JsonNode event) {
        String errorText = optionalString(event, "message");
        if (errorText == null) {
            errorText = optionalString(event, "error");
        }
        if (errorText == null) {
            errorText = event.get("type").asText();
        }
        return Collections.singletonList(new UIMessageChunk.Error(errorText));
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────────────

    @Nullable
    private static String optionalString(@Nullable JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        return value.isTextual() ? value.asText() : value.toString();
    }
}

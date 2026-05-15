package de.tum.in.www1.hephaestus.agent.mentor.chat.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import java.nio.charset.StandardCharsets;
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
public class PiEventToUiChunkTranslator {

    private static final Logger log = LoggerFactory.getLogger(PiEventToUiChunkTranslator.class);
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
            case "pi_error" -> handleError(piEvent, state);
            case "turn_watchdog_fired" -> handleWatchdogFired(state);
            case "session_persisted" -> handleSessionPersisted(piEvent, state);
            // Session-level events Pi emits that we intentionally drop. Listed explicitly so the
            // `default` arm can WARN on TRULY unknown types — silent default-drops hide protocol
            // drift (a new Pi event type would just disappear into DEBUG, never noticed).
            case
                "runner_ready",
                "agent_start",
                "turn_start",
                "tool_execution_update",
                "queue_update",
                "compaction_start",
                "compaction_end",
                "session_info_changed",
                "thinking_level_changed",
                "auto_retry_start",
                "auto_retry_end" -> List.of();
            default -> {
                log.warn("Unknown Pi event type '{}' — dropping. Protocol drift?", type);
                yield List.of();
            }
        };
    }

    // ─── session_persisted → capture verbatim Pi session JSONL into state ─────────────────

    private List<UIMessageChunk> handleSessionPersisted(JsonNode event, TranslatorState state) {
        String jsonl = optionalString(event, "jsonl");
        if (jsonl == null || jsonl.isEmpty()) {
            // WARN, not DEBUG: a missing payload silently breaks prompt-cache continuity on the
            // next cold restart. Surfaces runner regressions in logs instead of an invisible miss.
            log.warn("session_persisted carried no jsonl payload — next cold restart will use stale bytes");
            return List.of();
        }
        state.observeSessionJsonl(jsonl.getBytes(StandardCharsets.UTF_8));
        return List.of();
    }

    // ─── turn_watchdog_fired → Error with user-friendly text ──────────────────────────────

    private List<UIMessageChunk> handleWatchdogFired(TranslatorState state) {
        // Runner emits this when the per-turn budget is exceeded; the bare type string
        // ("turn_watchdog_fired") leaked to the UI as the error text. Map to a user-facing
        // message; the runner-side correlate is logged at WARN already. Close any open
        // text/reasoning block first so the AI SDK reducer doesn't crash on an `error` chunk
        // following an unmatched `*-start` (vercel/ai #11700).
        List<UIMessageChunk> out = new ArrayList<>(closeOpenStreamingBlocks(state));
        out.add(new UIMessageChunk.Error("Mentor turn timed out before completion."));
        return out;
    }

    // ─── message_end → no chunks, but captures the authoritative usage snapshot ───────────

    private List<UIMessageChunk> handleMessageEnd(JsonNode event, TranslatorState state) {
        // Per pi-coding-agent dist/core/extensions/types.d.ts MessageEndEvent.message: AgentMessage.
        // For assistant messages the final, authoritative `usage` + `model` + `stopReason` live
        // here. For tool result and user messages this branch is a no-op. We don't emit
        // UIMessageChunks; the turn-level Finish chunk on agent_end carries the metadata to the
        // client. Capturing `stopReason` here is the authoritative source — agent_end's walk
        // through messages[] is a fallback for runners that omit message_end.
        capturePartialUsage(event.path("message"), state);
        String stopReason = optionalString(event.path("message"), "stopReason");
        if (stopReason != null) {
            state.observeStopReason(stopReason);
        }
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
        // Decoupled Start vs StartStep: if the orchestrator pre-emitted the Start chunk (so the
        // webapp's useChat reducer can show a placeholder during sandbox cold-start), the
        // translator must NOT re-emit Start — AI-SDK dedupes by message id but the duplicate is
        // wire-noise. The StartStep, however, is per-Pi-message and must always fire because
        // useChat's reducer pushes a {type: "step-start"} part for each one. Pi may emit
        // multiple message_start events across a single turn (one per assistant message in a
        // multi-step tool-using turn), each of which gets its own step.
        boolean firstStart = !state.isStarted();
        if (firstStart) {
            state.markStarted();
        }
        state.incrementStep();
        if (firstStart) {
            return List.of(new UIMessageChunk.Start(state.assistantMessageId(), null), new UIMessageChunk.StartStep());
        }
        // Subsequent assistant message in the same turn (e.g. after a tool execution) — keep
        // the existing top-level Start the client already has, fire a fresh StartStep.
        return List.of(new UIMessageChunk.StartStep());
    }

    // ─── message_update: text_delta + thinking_delta ──────────────────────────────────────

    private List<UIMessageChunk> handleMessageUpdate(JsonNode event, TranslatorState state) {
        // Pi shape: {type:"message_update", message: AgentMessage, assistantMessageEvent: {...}}
        // See pi-coding-agent extensions/types.ts MessageUpdateEvent for the union.
        JsonNode ame = event.path("assistantMessageEvent");
        if (!ame.isObject() || !ame.has("type")) {
            log.debug("message_update without assistantMessageEvent — skipping: {}", event);
            return List.of();
        }
        return handleAssistantMessageEvent(ame, event, state);
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
            // Pi may interleave text + reasoning blocks within a single message_update stream.
            // `*_end` events are the authoritative close signal — without honouring them, a
            // reasoning block followed by a text block in the same message would never drain
            // its buffer into the persisted parts array (closeXBlock is the only path that
            // appends), so the reasoning text is LOST on multi-block messages. Open is lazy
            // (on first delta); close must respect the explicit end signal.
            case "text_end" -> closeTextIfMatches(blockId, state);
            case "thinking_end" -> closeReasoningIfMatches(blockId, state);
            // text_start / thinking_start are pure lifecycle markers; we open lazily on the
            // first delta, so the dedicated start events are no-ops. Tool calls surface via
            // the top-level `tool_execution_*` events.
            case
                "text_start",
                "thinking_start",
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

    /**
     * Emit {@code TextEnd(id)} if the open text block matches {@code blockId}; close the
     * accumulator (which flushes the buffered text into {@code partsAccumulator}) regardless.
     * No-op if no text block is open or the id doesn't match (defends against stray closes).
     */
    private List<UIMessageChunk> closeTextIfMatches(String blockId, TranslatorState state) {
        if (blockId == null || !blockId.equals(state.activeTextId())) return List.of();
        state.closeTextBlock();
        return List.of(new UIMessageChunk.TextEnd(blockId));
    }

    private List<UIMessageChunk> closeReasoningIfMatches(String blockId, TranslatorState state) {
        if (blockId == null || !blockId.equals(state.activeReasoningId())) return List.of();
        state.closeReasoningBlock();
        return List.of(new UIMessageChunk.ReasoningEnd(blockId));
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
        // Pi shape per pi-coding-agent extensions/types.ts ToolExecutionStartEvent:
        //   {type:"tool_execution_start", toolCallId, toolName, args}
        String toolCallId = optionalString(event, "toolCallId");
        String toolName = optionalString(event, "toolName");
        if (toolCallId == null || toolName == null) {
            log.debug("tool_execution_start missing toolCallId or toolName — skipping: {}", event);
            return List.of();
        }
        JsonNode input = event.has("args") && !event.get("args").isNull() ? event.get("args") : null;
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
        String toolCallId = optionalString(event, "toolCallId");
        if (toolCallId == null) {
            log.debug("tool_execution_end missing toolCallId — skipping: {}", event);
            return List.of();
        }
        boolean isError = event.path("isError").asBoolean(false);
        if (!isError) {
            JsonNode result = event.path("result");
            JsonNode output = !result.isMissingNode() && !result.isNull() ? result : null;
            state.recordToolCallOutput(toolCallId, output);
            return List.of(
                new UIMessageChunk.ToolOutputAvailable(toolCallId, output != null ? output : NODES.nullNode())
            );
        }
        String errorText = extractToolErrorText(event);
        state.recordToolCallError(toolCallId, errorText);
        return List.of(new UIMessageChunk.ToolOutputError(toolCallId, errorText));
    }

    /** Max tool-error length surfaced to the UI. Pi's tool result can ship the whole stderr
     *  (could be MBs). Truncating keeps the AI SDK error banner usable and bounds wire size. */
    static final int MAX_TOOL_ERROR_LEN = 1024;

    private static String extractToolErrorText(JsonNode event) {
        String raw = extractToolErrorRaw(event);
        if (raw == null) return "tool execution failed";
        if (raw.length() <= MAX_TOOL_ERROR_LEN) return raw;
        return raw.substring(0, MAX_TOOL_ERROR_LEN) + "…[truncated " + (raw.length() - MAX_TOOL_ERROR_LEN) + " chars]";
    }

    @Nullable
    private static String extractToolErrorRaw(JsonNode event) {
        // Pi tool-result shape: {result: {content: [{type:"text", text:"..."}]}}.
        JsonNode content = event.path("result").path("content");
        if (content.isArray() && !content.isEmpty()) {
            JsonNode first = content.get(0);
            if (first.isObject() && first.path("text").isTextual()) {
                return first.get("text").asText();
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
        // Last-assistant-wins inside the messages[] walk — covers the multi-step case where
        // agent_end ships all assistant messages of the turn. If none of them carries a
        // stopReason (e.g. a stub runner or a Pi build that emits only message_end), fall back
        // to the authoritative value captured on `message_end.message.stopReason` via state.
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
            piStopReason = state.observedStopReason();
        }
        List<UIMessageChunk> out = closeOpenStreamingBlocks(state);
        UIMessageChunk.MessageMetadata metadata = UIMessageChunk.MessageMetadata.of(
            state.observedModel(),
            UIMessageChunk.MessageMetadata.Usage.fromJsonNode(state.observedUsage()),
            /* costUsd, set by the persistence layer post-translation */ null
        );
        out.add(new UIMessageChunk.Finish(mapStopReason(piStopReason), metadata));
        return out;
    }

    /**
     * Map Pi {@code StopReason} ({@code stop|length|toolUse|error|aborted}, pi-ai/src/types.ts)
     * to the AI-SDK UIMessageStream {@code finishReason} enum
     * ({@code stop|length|content-filter|tool-calls|error|other}). The wider
     * {@code LanguageModelV2FinishReason} includes {@code "unknown"} but the chunk schema
     * does not — strict-zod parsing rejects it client-side. Unknown values map to {@code OTHER}.
     *
     * <p>{@code null} (Pi never reported a stop reason) maps to wire-null. Defaulting to
     * {@code STOP} would mask provider regressions (e.g. an upstream change dropping
     * {@code aborted}) and the AI-SDK schema accepts {@code finishReason} as optional.
     */
    @Nullable
    static UIMessageChunk.FinishReason mapStopReason(@Nullable String piStopReason) {
        if (piStopReason == null) return null;
        return switch (piStopReason) {
            case "stop" -> UIMessageChunk.FinishReason.STOP;
            case "length" -> UIMessageChunk.FinishReason.LENGTH;
            case "toolUse" -> UIMessageChunk.FinishReason.TOOL_CALLS;
            case "error", "aborted" -> UIMessageChunk.FinishReason.ERROR;
            default -> UIMessageChunk.FinishReason.OTHER;
        };
    }

    // ─── link_finding → DataFinding ──────────────────────────────────────────────────────

    private List<UIMessageChunk> handleLinkFinding(JsonNode event, TranslatorState state) {
        // Runner emits camelCase `findingId` (pi-mentor-runner.mjs defineLinkFindingTool).
        String findingIdStr = optionalString(event, "findingId");
        if (findingIdStr == null) {
            log.debug("link_finding missing findingId — skipping: {}", event);
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

    private List<UIMessageChunk> handleError(JsonNode event, TranslatorState state) {
        String errorText = optionalString(event, "message");
        if (errorText == null) {
            errorText = optionalString(event, "error");
        }
        if (errorText == null) {
            errorText = event.get("type").asText();
        }
        // Close any open text/reasoning block before the terminal error chunk: the AI SDK
        // reducer crashes on an `error` chunk that follows an unmatched `*-start` (vercel/ai #11700).
        List<UIMessageChunk> out = new ArrayList<>(closeOpenStreamingBlocks(state));
        out.add(new UIMessageChunk.Error(errorText));
        return out;
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────────────

    /**
     * Return the {@code field}'s text value, or {@code null} if absent, JSON-null, or a non-text
     * shape (object, array, number, boolean). Surfacing {@code value.toString()} for non-textual
     * nodes would mask shape drift — a future Pi schema change that ships an object where we
     * expect a string would silently leak {@code "{\"id\":\"...\"}"} into the LLM-visible field.
     */
    @Nullable
    private static String optionalString(@Nullable JsonNode node, String field) {
        if (node == null || !node.has(field)) return null;
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) return null;
        return value.asText();
    }
}

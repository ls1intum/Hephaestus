package de.tum.in.www1.hephaestus.agent.mentor.chat.wire;

import de.tum.in.www1.hephaestus.mentor.ChatThread;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * Mutable per-turn translator state. Built incrementally as Pi events stream in, snapshotted at
 * end-of-turn into the persisted {@code chat_message.parts} (and read on disconnect/timeout paths
 * to flush whatever has been observed so far).
 *
 * <p>Thread-safety: writes happen on the runner-event dispatcher thread; snapshots can be read
 * from the orchestrator's virtual thread on a disconnect race. Every method synchronises on the
 * instance monitor so an {@link #partsSnapshot()} deepCopy sees a stable {@link ArrayNode}
 * (which is NOT thread-safe).
 */
public final class TranslatorState {

    private final JsonNodeFactory nodes = JsonNodeFactory.instance;

    /** Assistant message id — passed back on the {@link UIMessageChunk.Start} chunk for reconciliation. */
    private final UUID assistantMessageId;

    @Nullable
    private String activeTextId;

    @Nullable
    private String activeReasoningId;

    /** Buffer of text so far for the open text block — used to materialise the final {@code text} part. */
    private final StringBuilder textBuffer = new StringBuilder();

    /** Buffer of reasoning so far — finalised when reasoning closes. */
    private final StringBuilder reasoningBuffer = new StringBuilder();

    /** Tool-call id → tool name, populated on {@code tool_execution_start} for later error labels. */
    private final Map<String, String> toolNameByCallId = new LinkedHashMap<>();

    /**
     * Tool-call id → the mutable {@code ObjectNode} part inside {@link #partsAccumulator}. The AI
     * SDK reducer mutates a single part across input-available → output-available/error; appending
     * separate parts breaks {@code getToolInvocation} which finds-by-toolCallId and returns the
     * first hit, so the user sees input but no output after page refresh.
     */
    private final Map<String, ObjectNode> toolPartByCallId = new LinkedHashMap<>();

    /** AI SDK UIMessage parts as accumulated. Order matches the stream; written to JSONB at end-of-turn. */
    private final ArrayNode partsAccumulator = nodes.arrayNode();

    /** Did we emit at least one {@code Start} chunk? Defensive — runner may replay an event. */
    private boolean started = false;

    /** Step counter — Pi internally tracks turns; we surface them as AI SDK steps for grouping. */
    private int stepDepth = 0;

    /**
     * Latest observed token usage snapshot from Pi message events. Pi emits running counts on
     * {@code message_update.assistantMessageEvent.partial.usage} and a final, authoritative
     * snapshot on {@code message_end.message.usage}. {@code agent_end} carries no usage of
     * its own (per pi-coding-agent/dist/core/extensions/types.d.ts AgentEndEvent — only
     * {@code messages: AgentMessage[]}). The translator threads the latest observation here so
     * the Finish chunk + persistence layer surface real cost.
     */
    @Nullable
    private JsonNode observedUsage;

    /** Model id observed on the first AssistantMessage; used for pricing lookup. */
    @Nullable
    private String observedModel;

    /**
     * Authoritative Pi {@code stopReason} captured on {@code message_end.message.stopReason}.
     * The translator's {@code handleAgentEnd} uses this when present, falling back to a walk
     * over {@code agent_end.messages[]} only when no message_end was emitted.
     */
    @Nullable
    private String observedStopReason;

    /** Verbatim Pi SDK session JSONL captured from {@code session_persisted}; see {@link ChatThread#getSessionJsonl}. */
    @Nullable
    private byte[] observedSessionJsonl;

    public TranslatorState(UUID assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public UUID assistantMessageId() {
        return assistantMessageId;
    }

    public synchronized boolean isStarted() {
        return started;
    }

    public synchronized void markStarted() {
        this.started = true;
    }

    public synchronized int incrementStep() {
        // Mirror the AI SDK reducer's `{type:"step-start"}` part so a rehydrated message renders
        // the same step boundaries the client built incrementally during streaming.
        ObjectNode stepStart = nodes.objectNode();
        stepStart.put("type", "step-start");
        partsAccumulator.add(stepStart);
        return ++stepDepth;
    }

    public synchronized int decrementStep() {
        // Pi may emit more turn_end than start-step (e.g. agent_end without a paired turn_end);
        // clamp the field itself, not just the return value, so the next incrementStep starts
        // from a sane base instead of climbing out of a negative hole.
        if (stepDepth > 0) {
            stepDepth--;
        }
        return stepDepth;
    }

    @Nullable
    public synchronized String activeTextId() {
        return activeTextId;
    }

    public synchronized void openTextBlock(String id) {
        this.activeTextId = id;
        this.textBuffer.setLength(0);
    }

    public synchronized void appendText(String delta) {
        this.textBuffer.append(delta);
    }

    public synchronized void closeTextBlock() {
        if (activeTextId != null && textBuffer.length() > 0) {
            ObjectNode part = nodes.objectNode();
            part.put("type", "text");
            part.put("text", textBuffer.toString());
            // "done" — terminal value of AI SDK's TextUIPart.state, so a rehydrated message
            // doesn't render an in-progress streaming cursor.
            part.put("state", "done");
            partsAccumulator.add(part);
        }
        this.activeTextId = null;
        this.textBuffer.setLength(0);
    }

    @Nullable
    public synchronized String activeReasoningId() {
        return activeReasoningId;
    }

    public synchronized void openReasoningBlock(String id) {
        this.activeReasoningId = id;
        this.reasoningBuffer.setLength(0);
    }

    public synchronized void appendReasoning(String delta) {
        this.reasoningBuffer.append(delta);
    }

    public synchronized void closeReasoningBlock() {
        if (activeReasoningId != null && reasoningBuffer.length() > 0) {
            ObjectNode part = nodes.objectNode();
            part.put("type", "reasoning");
            part.put("text", reasoningBuffer.toString());
            // AI SDK's ReasoningUIPart schema mirrors TextUIPart: {state:"streaming"|"done"} is
            // optional. We persist the terminal value for the same refresh-vs-live invariant.
            part.put("state", "done");
            partsAccumulator.add(part);
        }
        this.activeReasoningId = null;
        this.reasoningBuffer.setLength(0);
    }

    /**
     * Track tool name for the given call id so {@link UIMessageChunk.ToolOutputError} can include
     * it. Creates the part once; subsequent {@link #recordToolCallOutput}/{@link #recordToolCallError}
     * calls mutate it in place — matching how AI SDK's reducer threads a single part through
     * input-available → output-available/error.
     */
    public synchronized void recordToolCallStart(String toolCallId, String toolName, @Nullable JsonNode input) {
        if (toolCallId == null) return;
        if (toolName != null) {
            toolNameByCallId.put(toolCallId, toolName);
        }
        ObjectNode part = nodes.objectNode();
        part.put("type", "tool-" + (toolName != null ? toolName : "unknown"));
        part.put("toolCallId", toolCallId);
        part.put("state", "input-available");
        // `input` is REQUIRED on every state per AI SDK's zod schema (ai@6.0.3 dist/index.mjs:7682).
        // Default to an empty object so the schema validates even when Pi emits a parameter-less tool.
        part.set("input", input != null ? input : nodes.objectNode());
        partsAccumulator.add(part);
        toolPartByCallId.put(toolCallId, part);
    }

    /** Mutate the existing tool-call part to {@code state:"output-available"}. */
    public synchronized void recordToolCallOutput(String toolCallId, JsonNode output) {
        if (toolCallId == null) return;
        ObjectNode part = toolPartByCallId.get(toolCallId);
        if (part == null) {
            // Output without a prior start — synthesise a part. Should never happen with Pi's
            // matched start/end events, but if it ever does we want a coherent terminal state.
            part = nodes.objectNode();
            String toolName = toolNameByCallId.getOrDefault(toolCallId, "unknown");
            part.put("type", "tool-" + toolName);
            part.put("toolCallId", toolCallId);
            part.set("input", nodes.objectNode());
            partsAccumulator.add(part);
            toolPartByCallId.put(toolCallId, part);
        }
        part.put("state", "output-available");
        part.set("output", output != null ? output : nodes.nullNode());
    }

    /** Mutate the existing tool-call part to {@code state:"output-error"}. */
    public synchronized void recordToolCallError(String toolCallId, String errorText) {
        if (toolCallId == null) return;
        ObjectNode part = toolPartByCallId.get(toolCallId);
        if (part == null) {
            part = nodes.objectNode();
            String toolName = toolNameByCallId.getOrDefault(toolCallId, "unknown");
            part.put("type", "tool-" + toolName);
            part.put("toolCallId", toolCallId);
            part.set("input", nodes.objectNode());
            partsAccumulator.add(part);
            toolPartByCallId.put(toolCallId, part);
        }
        part.put("state", "output-error");
        part.put("errorText", errorText);
        part.remove("output");
    }

    public synchronized void recordDataFinding(UUID findingId) {
        // Match the AI SDK data-* envelope: {type, id, data:{...}}. The id at the top level
        // lets AI SDK dedupe across re-renders; findingId stays inside data for consumers.
        ObjectNode part = nodes.objectNode();
        part.put("type", "data-finding");
        part.put("id", findingId.toString());
        part.putObject("data").put("findingId", findingId.toString());
        partsAccumulator.add(part);
    }

    /**
     * Snapshot of the parts array; safe to persist to JSONB without further mutation. The
     * {@code synchronized} pairs with every mutator so a cross-thread snapshot from the
     * orchestrator vthread (timeout / disconnect / error paths) sees a stable
     * {@link ArrayNode} — {@code ArrayNode.deepCopy()} racing an {@code add(...)} from the
     * runner-event thread would otherwise surface as a {@code ConcurrentModificationException}.
     */
    public synchronized ArrayNode partsSnapshot() {
        return partsAccumulator.deepCopy();
    }

    /**
     * Record the latest usage snapshot from a Pi {@code AssistantMessage.usage} block. We hold
     * the deep-copied node so subsequent runner emissions can mutate the original without
     * leaking into persisted metadata. Each call overwrites — Pi's contract is that the latest
     * snapshot is monotonically more complete than the previous one (running totals).
     */
    public synchronized void observeUsage(JsonNode usage) {
        if (usage != null && usage.isObject() && !usage.isEmpty()) {
            this.observedUsage = usage.deepCopy();
        }
    }

    /** Record the assistant message's model id; first non-blank wins (model rarely changes mid-turn). */
    public synchronized void observeModel(@Nullable String model) {
        if (model != null && !model.isBlank() && this.observedModel == null) {
            this.observedModel = model;
        }
    }

    public synchronized boolean hasObservedUsage() {
        return observedUsage != null;
    }

    @Nullable
    public synchronized JsonNode observedUsage() {
        return observedUsage;
    }

    @Nullable
    public synchronized String observedModel() {
        return observedModel;
    }

    /**
     * Record the authoritative Pi stop reason observed on {@code message_end}. Last write wins
     * because multi-step turns can emit multiple message_end events; the latest one is the
     * terminal reason for the turn. Blank values are ignored to defend against an empty-field
     * runner emit overwriting a real prior reason.
     */
    public synchronized void observeStopReason(@Nullable String stopReason) {
        if (stopReason != null && !stopReason.isBlank()) {
            this.observedStopReason = stopReason;
        }
    }

    @Nullable
    public synchronized String observedStopReason() {
        return observedStopReason;
    }

    public synchronized void observeSessionJsonl(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        this.observedSessionJsonl = bytes.clone();
    }

    @Nullable
    public synchronized byte[] observedSessionJsonl() {
        return observedSessionJsonl;
    }
}

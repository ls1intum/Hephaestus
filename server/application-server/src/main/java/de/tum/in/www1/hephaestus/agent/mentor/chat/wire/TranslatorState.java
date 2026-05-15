package de.tum.in.www1.hephaestus.agent.mentor.chat.wire;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.Nullable;

/**
 * Mutable per-turn translator state. Created fresh by the orchestrator at the start of each turn
 * (one assistant message) and discarded once {@code Finish} is emitted. Owns:
 *
 * <ul>
 *   <li>Open block ids (text + reasoning) — set on first delta so the {@link UIMessageChunk.TextStart}
 *       lifecycle chunk fires exactly once per block. Pi's stream interleaves text and reasoning;
 *       both can be open simultaneously, each with its own id.</li>
 *   <li>Tool-call name lookup — Pi emits the tool name on {@code tool_execution_start} but not on
 *       {@code tool_execution_end}; we cache it so {@code ToolOutputError} can carry the friendly name.</li>
 *   <li>Accumulated parts — the AI SDK UIMessage shape stored verbatim in
 *       {@code chat_message.parts} once the turn completes. Built incrementally because building it
 *       post-hoc from chunks would require replaying every delta to concatenate the deltas back into
 *       a single text block, and we already have the in-flight pieces in memory.</li>
 * </ul>
 *
 * <p>Thread-safety: writes happen on the runner-event dispatcher thread; {@link #partsSnapshot()},
 * {@link #observedUsage()}, and {@link #observedModel()} can be read from the orchestrator's
 * virtual thread on timeout/disconnect/error paths to feed {@code persistence.interrupt(...)} a
 * coherent parts array. Every mutator + reader synchronises on the instance monitor so a snapshot
 * sees a stable {@code partsAccumulator} ({@link ArrayNode} is NOT thread-safe — a deepCopy racing
 * an {@code add(...)} from the writer would surface as a {@link java.util.ConcurrentModificationException}).
 * Locking is per-instance and short — every method body runs in nanoseconds.
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

    /**
     * Tool-call id → tool name, populated on {@code tool_execution_start} for later error labels.
     * Plain {@link LinkedHashMap}: the class doc above states the per-turn translator runs on a
     * single dispatcher thread; previous {@link java.util.concurrent.ConcurrentHashMap} bought
     * nothing while every other field (text buffers, step counter, observed usage) was a
     * non-thread-safe primitive — the type was theatre.
     */
    private final Map<String, String> toolNameByCallId = new LinkedHashMap<>();

    /**
     * Tool-call id → the mutable {@link ObjectNode} part inside {@link #partsAccumulator}. AI SDK's
     * {@code processUIMessageStream} reducer mutates a *single* part across the
     * input-available → output-available/error lifecycle (vercel/ai dist/index.mjs:4632-4666 for
     * @ai-sdk/react@3.0.3 ⇄ ai@6.0.3). Appending three separate parts (one per state) breaks
     * {@code getToolInvocation} which {@code find}s by toolCallId and returns the first hit —
     * the user sees the tool input but never the output after page refresh. We mirror the reducer
     * here so persisted parts round-trip through {@code safeValidateUIMessages}.
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

    /**
     * Verbatim Pi SDK session JSONL bytes captured from the runner's {@code session_persisted}
     * event (emitted on {@code agent_end} BEFORE the agent_end frame so this field is populated
     * by the time {@code MentorTurnPersistence.finalise} runs). Persisted into
     * {@code chat_thread.session_jsonl} BYTEA so a cold container can restore byte-identical
     * state on the user's next message — critical for provider prompt-cache hits (Anthropic +
     * OpenAI require byte-identical prefix), tool_use/tool_result pairing (Anthropic 400s on
     * orphaned tool_use), and thinking blocks (extended-thinking-2025-05-14 requires verbatim
     * thinking preservation across turns).
     */
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
        // AI SDK's client reducer pushes a `{type:"step-start"}` part for every `start-step` chunk
        // it sees (ai@6.0.3 dist/index.mjs:4988-5050). Mirror that here so the rehydrated message
        // — fetched via `GET /threads/{id}` — round-trips through `safeValidateUIMessages` and
        // renders the same step boundaries the client built incrementally during streaming.
        ObjectNode stepStart = nodes.objectNode();
        stepStart.put("type", "step-start");
        partsAccumulator.add(stepStart);
        return ++stepDepth;
    }

    public synchronized int decrementStep() {
        return Math.max(0, --stepDepth);
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
            // AI SDK's TextUIPart schema (vercel/ai packages/ai/src/ui/ui-messages.ts) allows
            // {state: "streaming" | "done"}. We persist the terminal value; "done" tells the
            // client renderer the part is final (vs an in-progress stream) so a refresh-vs-live
            // visual diff doesn't surface.
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

    /**
     * Capture the verbatim Pi SDK session JSONL bytes the runner shipped via
     * {@code session_persisted} on agent_end. The runner emits this event BEFORE the agent_end
     * frame so the bytes are available when {@code MentorTurnPersistence.finalise} runs.
     * Defensive-copies to defeat callers that mutate the array post-set.
     */
    public synchronized void observeSessionJsonl(@Nullable byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        this.observedSessionJsonl = bytes.clone();
    }

    /**
     * Return the captured session JSONL bytes, or {@code null} if the runner never emitted
     * {@code session_persisted} (e.g. protocol-only test stub, mid-stream abort before
     * agent_end, or the SDK never persisted the session). Callers must tolerate null —
     * persistence skips the blob write rather than clobbering a prior turn's bytes.
     */
    @Nullable
    public synchronized byte[] observedSessionJsonl() {
        // No defensive copy on read: the byte[] is treated as immutable by all production
        // callers (passed straight into ChatThreadRepository.updateSessionJsonl). Cloning here
        // would double memory for the largest field in the state on every read.
        return observedSessionJsonl;
    }
}

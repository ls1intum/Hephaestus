package de.tum.cit.aet.hephaestus.agent.mentor.chat.wire;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.mentor.ChatThread;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
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

    private @Nullable String activeTextId;

    /** Buffer of text so far for the open text block — used to materialise the final {@code text} part. */
    private final StringBuilder textBuffer = new StringBuilder();

    /** AI SDK UIMessage parts as accumulated. Order matches the stream; written to JSONB at end-of-turn. */
    private final ArrayNode partsAccumulator = nodes.arrayNode();

    /**
     * Observation ids the mentor linked this turn via {@code link_observation}, in emission order. Read at
     * end-of-turn by the conversational-delivery reconciler to flip the matching PREPARED unit to DELIVERED.
     */
    private final List<UUID> linkedObservationIds = new ArrayList<>();

    /** Did we emit at least one {@code Start} chunk? Defensive — runner may replay an event. */
    private boolean started = false;

    private boolean llmCallStarted = false;

    /** Step counter — Pi internally tracks turns; we surface them as AI SDK steps for grouping. */
    private int stepDepth = 0;

    private final ObjectNode completedUsage = nodes.objectNode();

    @Nullable
    private JsonNode currentUsage;

    private int completedCallCount;

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
    private byte@Nullable [] observedSessionJsonl;

    /**
     * Which connection funds this turn's LLM calls, frozen at turn start. Unsynchronized unlike the
     * streaming mutators below: written once, before any runner event can race it.
     */
    @Nullable
    private FundingSource connectionScope;

    @Nullable
    private Long connectionId;

    @Nullable
    private String admittedModel;

    @Nullable
    private LlmPriceSnapshot admittedPrice;

    public TranslatorState(UUID assistantMessageId) {
        this.assistantMessageId = assistantMessageId;
    }

    public void bindConnection(@Nullable FundingSource connectionScope, @Nullable Long connectionId) {
        this.connectionScope = connectionScope;
        this.connectionId = connectionId;
    }

    public void bindAdmission(String model, @Nullable LlmPriceSnapshot price) {
        this.admittedModel = model;
        this.admittedPrice = price;
    }

    public @Nullable String admittedModel() {
        return admittedModel;
    }

    public @Nullable LlmPriceSnapshot admittedPrice() {
        return admittedPrice;
    }

    @Nullable
    public FundingSource connectionScope() {
        return connectionScope;
    }

    @Nullable
    public Long connectionId() {
        return connectionId;
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

    public synchronized void markLlmCallStarted() {
        this.llmCallStarted = true;
    }

    public synchronized boolean hasLlmCallStarted() {
        return llmCallStarted;
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

    public synchronized @Nullable String activeTextId() {
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

    public synchronized void recordDataObservation(UUID observationId) {
        // Match the AI SDK data-* envelope: {type, id, data:{...}}. The id at the top level
        // lets AI SDK dedupe across re-renders; observationId stays inside data for consumers.
        ObjectNode part = nodes.objectNode();
        part.put("type", "data-observation");
        part.put("id", observationId.toString());
        part.putObject("data").put("observationId", observationId.toString());
        partsAccumulator.add(part);
        linkedObservationIds.add(observationId);
    }

    /**
     * The observation ids the mentor linked this turn via {@code link_observation}, in emission order (duplicates
     * retained - the reconciler de-duplicates). Snapshot copy for cross-thread safety on the finalise path.
     */
    public synchronized List<UUID> linkedObservationIds() {
        return List.copyOf(linkedObservationIds);
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

    public synchronized void observeUsage(JsonNode usage) {
        if (usage != null && usage.isObject() && !usage.isEmpty()) {
            this.currentUsage = usage.deepCopy();
        }
    }

    public synchronized void completeUsage(JsonNode usage) {
        if (usage != null && usage.isObject() && !usage.isEmpty()) {
            addUsage(completedUsage, usage);
            completedCallCount++;
        }
        currentUsage = null;
    }

    /** Authoritative totals from {@code agent_end}; a runner may omit them, keeping what was streamed. */
    public synchronized void replaceCompletedUsage(List<JsonNode> usages) {
        if (usages.isEmpty()) return;
        completedUsage.removeAll();
        for (JsonNode usage : usages) {
            addUsage(completedUsage, usage);
        }
        completedCallCount = usages.size();
        currentUsage = null;
    }

    /** Record the assistant message's model id; first non-blank wins (model rarely changes mid-turn). */
    public synchronized void observeModel(@Nullable String model) {
        if (model != null && !model.isBlank() && this.observedModel == null) {
            this.observedModel = model;
        }
    }

    private boolean hasObservedUsage() {
        return !completedUsage.isEmpty() || currentUsage != null;
    }

    @Nullable
    public synchronized JsonNode observedUsage() {
        if (!hasObservedUsage()) return null;
        ObjectNode total = completedUsage.deepCopy();
        if (currentUsage != null) addUsage(total, currentUsage);
        return total;
    }

    /** Calls represented by {@link #observedUsage()}, including an interrupted in-progress call. */
    public synchronized int observedCallCount() {
        return completedCallCount + (currentUsage != null ? 1 : 0);
    }

    private static void addUsage(ObjectNode target, JsonNode source) {
        source
            .properties()
            .forEach(entry -> {
                String name = entry.getKey();
                JsonNode value = entry.getValue();
                if (value.isObject()) {
                    ObjectNode nested =
                        target.has(name) && target.get(name).isObject()
                            ? (ObjectNode) target.get(name)
                            : target.putObject(name);
                    addUsage(nested, value);
                } else if (value.isIntegralNumber()) {
                    long existing = target.has(name) && target.get(name).isNumber() ? target.get(name).asLong() : 0L;
                    target.put(name, existing + value.asLong());
                } else if (value.isFloatingPointNumber()) {
                    double existing =
                        target.has(name) && target.get(name).isNumber() ? target.get(name).asDouble() : 0D;
                    target.put(name, existing + value.asDouble());
                }
            });
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

    public synchronized void observeSessionJsonl(byte@Nullable [] bytes) {
        if (bytes == null || bytes.length == 0) {
            return;
        }
        this.observedSessionJsonl = bytes.clone();
    }

    public synchronized byte@Nullable [] observedSessionJsonl() {
        return observedSessionJsonl;
    }
}

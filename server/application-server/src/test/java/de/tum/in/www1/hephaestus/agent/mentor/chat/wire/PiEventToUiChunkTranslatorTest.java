package de.tum.in.www1.hephaestus.agent.mentor.chat.wire;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Real-shape coverage for {@link PiEventToUiChunkTranslator}. Every test loads a fixture file from
 * {@code src/test/resources/agent/mentor/pi-events/} that mirrors the on-the-wire JSON Pi emits
 * — verified against {@code @earendil-works/pi-coding-agent} dist .d.ts shapes. We intentionally
 * do NOT shape the fixtures from what the runner's protocol-only stub produces; doing that is
 * the test theater that hid the original bugs (translator read snake_case `delta_type`, real Pi
 * sends camelCase `assistantMessageEvent.type`).
 *
 * <p>If you change a Pi event mapping, change the fixture too. Stub-only shapes (the synthetic
 * {@code pi_error} / {@code turn_watchdog_fired} the runner itself emits) are exercised inline.
 */
@DisplayName("PiEventToUiChunkTranslator (real Pi shapes)")
class PiEventToUiChunkTranslatorTest extends BaseUnitTest {

    private static final String FIXTURE_DIR = "agent/mentor/pi-events/";

    private final ObjectMapper mapper = new ObjectMapper();
    private PiEventToUiChunkTranslator translator;
    private TranslatorState state;
    private final UUID assistantMessageId = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @BeforeEach
    void setUp() {
        translator = new PiEventToUiChunkTranslator();
        state = new TranslatorState(assistantMessageId);
    }

    private JsonNode fixture(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(FIXTURE_DIR + name)) {
            assertThat(in).as("fixture %s must exist on the test classpath", name).isNotNull();
            return mapper.readTree(in);
        }
    }

    // ─── message_start ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("message_start with assistant message → Start + StartStep and captures model")
    void messageStart_assistant_emitsStartAndCapturesModel() throws Exception {
        JsonNode event = fixture("message_start_assistant.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("Start", "StartStep");
        UIMessageChunk.Start start = (UIMessageChunk.Start) out.get(0);
        assertThat(start.messageId()).isEqualTo(assistantMessageId);
        // model lives on message.role-bearing object, NOT a top-level event field — this is the
        // bug the original audit exposed.
        assertThat(state.observedModel()).isEqualTo("claude-3-5-haiku-20241022");
    }

    @Test
    @DisplayName("message_start with non-assistant role → no chunks")
    void messageStart_userMessage_dropped() throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "message_start");
        event.putObject("message").put("role", "user");
        assertThat(translator.translate(event, state)).isEmpty();
        assertThat(state.isStarted()).isFalse();
    }

    @Test
    @DisplayName("message_start when orchestrator already markStarted: emits StartStep only (no duplicate Start)")
    void messageStart_afterOrchestratorMarkedStarted_emitsStartStepOnly() throws Exception {
        // Orchestrator pre-emits Start at turn open, then markStarted() to suppress duplicate.
        state.markStarted();
        JsonNode event = fixture("message_start_assistant.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("StartStep");
    }

    @Test
    @DisplayName("message_start fired twice in one turn (multi-step turn): Start once, StartStep per step")
    void messageStart_multipleAssistantMessages_secondEmitsStartStepOnly() throws Exception {
        JsonNode event = fixture("message_start_assistant.json");

        List<UIMessageChunk> first = translator.translate(event, state);
        List<UIMessageChunk> second = translator.translate(event, state);

        assertThat(first)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("Start", "StartStep");
        assertThat(second)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("StartStep");
    }

    // ─── message_update (Pi's authoritative shape) ───────────────────────────────────────

    @Test
    @DisplayName("text_delta translates to TextStart + TextDelta and observes usage")
    void delta_type_translatesTo_textDelta_chunk() throws Exception {
        JsonNode event = fixture("message_update_text_delta.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextStart", "TextDelta");
        UIMessageChunk.TextDelta delta = (UIMessageChunk.TextDelta) out.get(1);
        assertThat(delta.delta()).isEqualTo("hel");
        // contentIndex=0 → block id "text-0" (stable across deltas).
        assertThat(((UIMessageChunk.TextStart) out.get(0)).id()).isEqualTo("text-0");
        assertThat(state.observedUsage()).isNotNull();
        assertThat(state.observedUsage().get("input").asInt()).isEqualTo(12);
        assertThat(state.observedModel()).isEqualTo("claude-3-5-haiku-20241022");
    }

    @Test
    @DisplayName("Subsequent text_delta with same contentIndex reuses the open block id")
    void delta_type_reusesBlockIdAcrossDeltas() throws Exception {
        JsonNode first = fixture("message_update_text_delta.json");
        translator.translate(first, state);

        ObjectNode second = (ObjectNode) first.deepCopy();
        ((ObjectNode) second.get("assistantMessageEvent")).put("delta", "lo");
        List<UIMessageChunk> out = translator.translate(second, state);

        // Just a TextDelta — TextStart already fired on the first delta. This is the second
        // production-failure mode the audit caught: missing contentIndex → a fresh UUID per
        // delta → AI SDK reconciler can't merge them.
        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextDelta");
        assertThat(((UIMessageChunk.TextDelta) out.get(0)).id()).isEqualTo("text-0");
    }

    @Test
    @DisplayName("thinking_delta opens reasoning block with reasoning-<contentIndex> id")
    void delta_type_translatesTo_thinkingDelta_chunk() throws Exception {
        JsonNode event = fixture("message_update_thinking_delta.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("ReasoningStart", "ReasoningDelta");
        assertThat(((UIMessageChunk.ReasoningStart) out.get(0)).id()).isEqualTo("reasoning-0");
        assertThat(((UIMessageChunk.ReasoningDelta) out.get(1)).delta()).isEqualTo("Let me think");
    }

    // ─── message_end (captures final usage but emits no UI chunks) ───────────────────────

    @Test
    @DisplayName("message_end captures the final usage snapshot but emits no chunks")
    void messageEnd_capturesUsage_emitsNothing() throws Exception {
        JsonNode event = fixture("message_end_assistant.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out).isEmpty();
        assertThat(state.observedUsage()).isNotNull();
        assertThat(state.observedUsage().get("output").asInt()).isEqualTo(5);
        assertThat(state.observedUsage().get("cacheRead").asInt()).isEqualTo(2);
        assertThat(state.observedUsage().path("cost").path("total").asDouble()).isEqualTo(0.00002715);
        assertThat(state.observedModel()).isEqualTo("claude-3-5-haiku-20241022");
    }

    // ─── tool_execution_start / end with real camelCase shapes ───────────────────────────

    @Test
    @DisplayName("tool_execution_start (real Pi camelCase) emits ToolInputStart + ToolInputAvailable")
    void tool_call_start_emits_inputAvailable() throws Exception {
        JsonNode event = fixture("tool_execution_start.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("ToolInputStart", "ToolInputAvailable");
        UIMessageChunk.ToolInputStart startChunk = (UIMessageChunk.ToolInputStart) out.get(0);
        assertThat(startChunk.toolCallId()).isEqualTo("call-real-1");
        assertThat(startChunk.toolName()).isEqualTo("fetch_context");
        UIMessageChunk.ToolInputAvailable avail = (UIMessageChunk.ToolInputAvailable) out.get(1);
        assertThat(avail.input().get("path").asText()).isEqualTo("workspace.json");
    }

    @Test
    @DisplayName("tool_execution_end with isError:false emits ToolOutputAvailable")
    void tool_call_success_emits_outputAvailable() throws Exception {
        translator.translate(fixture("tool_execution_start.json"), state);
        JsonNode event = fixture("tool_execution_end_success.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out).hasSize(1);
        UIMessageChunk.ToolOutputAvailable avail = (UIMessageChunk.ToolOutputAvailable) out.get(0);
        assertThat(avail.output().path("content").get(0).get("text").asText()).isEqualTo("{\"workspace\":{}}");
    }

    @Test
    @DisplayName("tool_execution_end with isError:true emits ToolOutputError carrying the result.content text")
    void tool_call_failure_emits_outputError_withResultText() throws Exception {
        // Start a different call id so name lookup hits.
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "tool_execution_start");
        start.put("toolCallId", "call-real-2");
        start.put("toolName", "fetch_context");
        start.putObject("args").put("path", "foo");
        translator.translate(start, state);

        JsonNode event = fixture("tool_execution_end_error.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out).hasSize(1);
        UIMessageChunk.ToolOutputError err = (UIMessageChunk.ToolOutputError) out.get(0);
        assertThat(err.errorText()).contains("unauthorised_path");
    }

    @Test
    @DisplayName("tool_execution_end with neither isError nor error → treated as success")
    void tool_call_end_unknownStatus_treatedAsSuccess() throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "tool_execution_end");
        event.put("toolCallId", "x");
        event.putObject("result").putArray("content").addObject().put("type", "text").put("text", "ok");
        List<UIMessageChunk> out = translator.translate(event, state);
        assertThat(out).hasSize(1);
        assertThat(out.get(0)).isInstanceOf(UIMessageChunk.ToolOutputAvailable.class);
    }

    // ─── agent_end (no usage on event itself — harvest from messages[]) ──────────────────

    @Test
    @DisplayName("agent_end has no usage on the event — harvests from messages[].assistant.usage")
    void agentEnd_noEventUsage_harvestsFromMessages() throws Exception {
        JsonNode event = fixture("agent_end_no_usage.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) out.get(out.size() - 1);
        assertThat(finish.messageMetadata()).isNotNull();
        assertThat(finish.messageMetadata().model()).isEqualTo("claude-3-5-haiku-20241022");
        assertThat(finish.messageMetadata().usage()).isNotNull();
        assertThat(finish.messageMetadata().usage().input()).isEqualTo(25);
        assertThat(finish.messageMetadata().usage().output()).isEqualTo(5);
    }

    @Test
    @DisplayName("agent_end after message_end uses message_end's authoritative usage (not messages[])")
    void agentEnd_prefersMessageEndUsage() throws Exception {
        translator.translate(fixture("message_end_assistant.json"), state);
        // Override the agent_end messages with a wildly different usage to prove we don't trust it.
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "agent_end");
        event
            .putArray("messages")
            .addObject()
            .put("role", "assistant")
            .putObject("usage")
            .put("input", 999)
            .put("output", 999);

        List<UIMessageChunk> out = translator.translate(event, state);

        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) out.get(out.size() - 1);
        // message_end snapshot wins.
        assertThat(finish.messageMetadata().usage().input()).isEqualTo(25);
    }

    @Test
    @DisplayName("agent_end with empty messages[] → Finish with no metadata (cost stays null downstream)")
    void agentEnd_emptyMessages_noMetadata() throws Exception {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "agent_end");
        event.putArray("messages");
        List<UIMessageChunk> out = translator.translate(event, state);
        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) out.get(out.size() - 1);
        // No model, no usage, no top-level cost — the Finish carries null metadata.
        assertThat(finish.messageMetadata()).isNull();
    }

    @Test
    @DisplayName("agent_end maps Pi stopReason to AI SDK enum (toolUse → TOOL_CALLS, aborted → ERROR)")
    void agentEnd_mapsStopReasonToAiSdkEnum() {
        // Pi StopReason union: stop | length | toolUse | error | aborted (pi-ai/src/types.ts:269)
        // AI SDK union: stop | length | content-filter | tool-calls | error | other (ui-message-chunks.ts)
        // Anything raw-Pi reaching the wire would be a strict-zod rejection at the client.
        assertThat(PiEventToUiChunkTranslator.mapStopReason("stop")).isSameAs(UIMessageChunk.FinishReason.STOP);
        assertThat(PiEventToUiChunkTranslator.mapStopReason("length")).isSameAs(UIMessageChunk.FinishReason.LENGTH);
        assertThat(PiEventToUiChunkTranslator.mapStopReason("toolUse")).isSameAs(
            UIMessageChunk.FinishReason.TOOL_CALLS
        );
        assertThat(PiEventToUiChunkTranslator.mapStopReason("error")).isSameAs(UIMessageChunk.FinishReason.ERROR);
        assertThat(PiEventToUiChunkTranslator.mapStopReason("aborted")).isSameAs(UIMessageChunk.FinishReason.ERROR);
        assertThat(PiEventToUiChunkTranslator.mapStopReason("future-pi-reason")).isSameAs(
            UIMessageChunk.FinishReason.OTHER
        );
        // Null Pi-side stopReason → wire-null (AI-SDK schema accepts finishReason as optional).
        // Don't default to STOP — that would mask provider regressions that drop the field.
        assertThat(PiEventToUiChunkTranslator.mapStopReason(null)).isNull();
    }

    @Test
    @DisplayName("agent_end extracts stopReason from the last assistant message in messages[]")
    void agentEnd_extractsStopReasonFromLastAssistant() {
        ObjectNode event = mapper.createObjectNode();
        event.put("type", "agent_end");
        var msgs = event.putArray("messages");
        msgs.addObject().put("role", "user").put("stopReason", "irrelevant");
        msgs.addObject().put("role", "assistant").put("stopReason", "toolUse");
        msgs.addObject().put("role", "assistant").put("stopReason", "stop"); // last wins

        List<UIMessageChunk> out = translator.translate(event, state);

        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) out.get(out.size() - 1);
        assertThat(finish.finishReason()).isSameAs(UIMessageChunk.FinishReason.STOP);
    }

    // ─── turn_end + open block closure ───────────────────────────────────────────────────

    @Test
    @DisplayName("turn_end closes both text and reasoning blocks then emits FinishStep")
    void turnEnd_closesAllOpenBlocks() throws Exception {
        translator.translate(fixture("message_update_text_delta.json"), state);
        translator.translate(fixture("message_update_thinking_delta.json"), state);

        List<UIMessageChunk> out = translator.translate(mapper.readTree("{\"type\":\"turn_end\"}"), state);

        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextEnd", "ReasoningEnd", "FinishStep");
    }

    @Test
    @DisplayName("tool_execution_start closes any open text block before opening the tool")
    void toolStart_closesOpenText() throws Exception {
        translator.translate(fixture("message_update_text_delta.json"), state);
        List<UIMessageChunk> out = translator.translate(fixture("tool_execution_start.json"), state);
        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextEnd", "ToolInputStart", "ToolInputAvailable");
    }

    // ─── link_finding (runner-emitted, camelCase canonical) ──────────────────────────────

    @Test
    @DisplayName("link_finding (camelCase findingId) emits DataFinding")
    void linkFinding_camelCase_emitsDataFinding() throws Exception {
        JsonNode event = fixture("runner_link_finding.json");

        List<UIMessageChunk> out = translator.translate(event, state);

        assertThat(out).hasSize(1);
        UIMessageChunk.DataFinding df = (UIMessageChunk.DataFinding) out.get(0);
        assertThat(df.data().findingId()).isEqualTo(UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));
        assertThat(df.id()).isEqualTo(df.data().findingId());
    }

    @Test
    @DisplayName("link_finding with invalid UUID is dropped")
    void linkFinding_invalidUuid_dropped() throws Exception {
        assertThat(
            translator.translate(mapper.readTree("{\"type\":\"link_finding\",\"findingId\":\"nope\"}"), state)
        ).isEmpty();
    }

    // ─── synthetic runner events (snake-case, runner-owned) ──────────────────────────────

    @Test
    @DisplayName("pi_error (synthetic from runner) → Error chunk")
    void piError_emitsError() throws Exception {
        JsonNode event = mapper.readTree("{\"type\":\"pi_error\",\"error\":\"upstream timeout\"}");
        List<UIMessageChunk> out = translator.translate(event, state);
        assertThat(out).hasSize(1).first().isInstanceOf(UIMessageChunk.Error.class);
    }

    @Test
    @DisplayName("turn_watchdog_fired → Error chunk")
    void watchdogFired_emitsError() throws Exception {
        JsonNode event = mapper.readTree("{\"type\":\"turn_watchdog_fired\",\"threadId\":\"t\"}");
        assertThat(translator.translate(event, state)).hasSize(1).first().isInstanceOf(UIMessageChunk.Error.class);
    }

    @Test
    @DisplayName("runner_ready is dropped (controller observes it separately)")
    void runnerReady_dropped() throws Exception {
        assertThat(translator.translate(mapper.readTree("{\"type\":\"runner_ready\"}"), state)).isEmpty();
    }

    @Test
    @DisplayName("Unknown event type returns empty (forward-compat)")
    void unknownEvent_dropped() throws Exception {
        assertThat(translator.translate(mapper.readTree("{\"type\":\"future_event_we_dont_know\"}"), state)).isEmpty();
    }

    @Test
    @DisplayName("Pi session-level events are explicit no-ops (not silent default-drops)")
    void piSessionLevelEvents_explicitlyDropped() throws Exception {
        // These types are emitted by Pi but produce no UI chunks. Listing them as explicit
        // `case` arms lets the `default` arm WARN on TRULY unknown types — otherwise a new
        // Pi event variant would silently sail through as DEBUG. Each one must return empty.
        String[] sessionEvents = {
            "agent_start",
            "turn_start",
            "tool_execution_update",
            "queue_update",
            "compaction_start",
            "compaction_end",
            "session_info_changed",
            "thinking_level_changed",
            "auto_retry_start",
            "auto_retry_end",
            "runner_ready",
        };
        for (String type : sessionEvents) {
            JsonNode event = mapper.readTree("{\"type\":\"" + type + "\"}");
            assertThat(translator.translate(event, state))
                .as("session-level event %s must be an explicit no-op", type)
                .isEmpty();
        }
    }

    @Test
    @DisplayName("pi_error mid-stream closes the open text block before emitting Error (vercel/ai #11700)")
    void piError_closesOpenStreamingBlock() throws Exception {
        // Drive a text-start + delta so an open block exists, then fire pi_error. The AI SDK
        // client reducer crashes on an `error` chunk that follows an unmatched `*-start` — we
        // must emit `text-end` first.
        JsonNode start = fixture("message_start_assistant.json");
        translator.translate(start, state);
        JsonNode delta = fixture("message_update_text_delta.json");
        translator.translate(delta, state);
        JsonNode err = mapper.readTree("{\"type\":\"pi_error\",\"error\":\"upstream timeout\"}");
        List<UIMessageChunk> out = translator.translate(err, state);
        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextEnd", "Error");
    }

    @Test
    @DisplayName("turn_watchdog_fired mid-stream closes the open text block before emitting Error")
    void watchdogFired_closesOpenStreamingBlock() throws Exception {
        JsonNode start = fixture("message_start_assistant.json");
        translator.translate(start, state);
        JsonNode delta = fixture("message_update_text_delta.json");
        translator.translate(delta, state);
        JsonNode watchdog = mapper.readTree("{\"type\":\"turn_watchdog_fired\"}");
        List<UIMessageChunk> out = translator.translate(watchdog, state);
        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("TextEnd", "Error");
    }

    @Test
    @DisplayName("turn_watchdog_fired surfaces a user-friendly Error string (not the bare type symbol)")
    void turnWatchdogFired_userFriendlyText() throws Exception {
        JsonNode event = mapper.readTree("{\"type\":\"turn_watchdog_fired\",\"threadId\":\"t\"}");
        List<UIMessageChunk> out = translator.translate(event, state);
        assertThat(out).hasSize(1).first().isInstanceOf(UIMessageChunk.Error.class);
        UIMessageChunk.Error err = (UIMessageChunk.Error) out.get(0);
        // Must be human-readable, not the wire symbol "turn_watchdog_fired" (the pre-fix bug).
        assertThat(err.errorText()).doesNotContain("turn_watchdog_fired").contains("timed out");
    }

    @Test
    @DisplayName("decrementStep does not corrupt the step depth below zero (regression)")
    void decrementStep_clampsField() {
        // Pi can emit more turn_end than start-step (e.g. agent_end without a paired turn_end).
        // The field itself must clamp at 0; if it goes negative, the next incrementStep would
        // return 0 instead of 1 and the step-start part would not advance.
        state.decrementStep();
        state.decrementStep();
        int after = state.incrementStep();
        assertThat(after).as("incrementStep after over-decrements should yield 1, not 0").isEqualTo(1);
    }

    @Test
    @DisplayName("message_end captures stopReason; agent_end uses it as fallback when messages[] omits it")
    void messageEndStopReason_consumedByAgentEnd() throws Exception {
        // message_end carries the authoritative stopReason for the assistant message.
        ObjectNode messageEnd = mapper.createObjectNode();
        messageEnd.put("type", "message_end");
        ObjectNode msg = messageEnd.putObject("message");
        msg.put("role", "assistant");
        msg.put("stopReason", "length");
        translator.translate(messageEnd, state);
        assertThat(state.observedStopReason()).isEqualTo("length");

        // agent_end with NO stopReason on messages[] uses the captured value.
        ObjectNode agentEnd = mapper.createObjectNode();
        agentEnd.put("type", "agent_end");
        agentEnd.putArray("messages");
        List<UIMessageChunk> out = translator.translate(agentEnd, state);
        UIMessageChunk.Finish finish = (UIMessageChunk.Finish) out.get(out.size() - 1);
        assertThat(finish.finishReason()).isSameAs(UIMessageChunk.FinishReason.LENGTH);
    }

    @Test
    @DisplayName("text_end / thinking_end close their open blocks so multi-block messages preserve reasoning text")
    void textEndThinkingEnd_closeBlocksFlushesBuffers() throws Exception {
        // Open reasoning block first, append, then end it (multi-block scenario).
        ObjectNode reasoningDelta = mapper.createObjectNode();
        reasoningDelta.put("type", "message_update");
        ObjectNode ame1 = reasoningDelta.putObject("assistantMessageEvent");
        ame1.put("type", "thinking_delta");
        ame1.put("contentIndex", 0);
        ame1.put("delta", "Let me think");
        translator.translate(reasoningDelta, state);

        ObjectNode reasoningEnd = mapper.createObjectNode();
        reasoningEnd.put("type", "message_update");
        ObjectNode ame2 = reasoningEnd.putObject("assistantMessageEvent");
        ame2.put("type", "thinking_end");
        ame2.put("contentIndex", 0);
        List<UIMessageChunk> out = translator.translate(reasoningEnd, state);
        assertThat(out)
            .extracting(c -> c.getClass().getSimpleName())
            .containsExactly("ReasoningEnd");

        // The reasoning text must be drained into the persisted parts array — the pre-fix bug
        // was that this only happened on turn_end, so a multi-block message with reasoning
        // followed by text would lose the reasoning content.
        assertThat(state.partsSnapshot().toString()).contains("Let me think");
    }

    @Test
    @DisplayName("Malformed event with no type returns empty list")
    void malformedEvent_dropped() {
        assertThat(translator.translate(mapper.createObjectNode().put("noType", true), state)).isEmpty();
    }

    // ─── parts accumulation ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("State accumulates step-start + one text part with concatenated deltas")
    void stateAccumulatesPartsAcrossDeltas() throws Exception {
        translator.translate(fixture("message_start_assistant.json"), state);
        translator.translate(fixture("message_update_text_delta.json"), state);
        // Append another delta with same contentIndex
        ObjectNode second = (ObjectNode) fixture("message_update_text_delta.json").deepCopy();
        ((ObjectNode) second.get("assistantMessageEvent")).put("delta", "lo");
        translator.translate(second, state);
        translator.translate(mapper.readTree("{\"type\":\"turn_end\"}"), state);

        // AI SDK's reducer pushes {type:"step-start"} on every start-step chunk; we mirror that
        // here so the persisted UIMessage round-trips correctly through safeValidateUIMessages.
        JsonNode snapshot = state.partsSnapshot();
        assertThat(snapshot.isArray()).isTrue();
        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(0).get("type").asText()).isEqualTo("step-start");
        assertThat(snapshot.get(1).get("type").asText()).isEqualTo("text");
        assertThat(snapshot.get(1).get("text").asText()).isEqualTo("hello");
    }

    // ─── tool-call part mutation (single part per toolCallId across states) ──────────────

    @Test
    @DisplayName("Tool execution start+end produces ONE persisted part that mutates state in place")
    void toolPart_mutatesSingleEntryAcrossLifecycle() throws Exception {
        // 1) start
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "tool_execution_start");
        start.put("toolCallId", "tc-1");
        start.put("toolName", "fetch_context");
        start.putObject("args").put("path", "workspace.json");
        translator.translate(start, state);

        // 2) success end
        ObjectNode end = mapper.createObjectNode();
        end.put("type", "tool_execution_end");
        end.put("toolCallId", "tc-1");
        end.put("toolName", "fetch_context");
        end.put("isError", false);
        end.putObject("result").putArray("content").addObject().put("type", "text").put("text", "ok");
        translator.translate(end, state);

        JsonNode snapshot = state.partsSnapshot();
        // Exactly one tool part — AI SDK's reducer keys parts by toolCallId, so triplicating
        // start/output/error parts would break getToolInvocation() (which find()s the first
        // toolCallId hit and would surface the input-available state forever).
        assertThat(snapshot).hasSize(1);
        JsonNode part = snapshot.get(0);
        assertThat(part.get("type").asText()).isEqualTo("tool-fetch_context");
        assertThat(part.get("toolCallId").asText()).isEqualTo("tc-1");
        assertThat(part.get("state").asText()).isEqualTo("output-available");
        assertThat(part.has("input")).isTrue();
        assertThat(part.has("output")).isTrue();
    }

    @Test
    @DisplayName("Tool start then error mutates the same part; `output` is removed and errorText set")
    void toolPart_errorMutationCleansOutput() throws Exception {
        ObjectNode start = mapper.createObjectNode();
        start.put("type", "tool_execution_start");
        start.put("toolCallId", "tc-2");
        start.put("toolName", "fetch_context");
        start.putObject("args");
        translator.translate(start, state);

        // Pi's canonical tool-result shape: {result: {content: [{type:"text", text:"..."}]}}.
        // The translator extracts the first content[].text as the user-facing errorText.
        ObjectNode end = mapper.createObjectNode();
        end.put("type", "tool_execution_end");
        end.put("toolCallId", "tc-2");
        end.put("isError", true);
        end
            .putObject("result")
            .putArray("content")
            .addObject()
            .put("type", "text")
            .put("text", "fetch_context timed out");
        translator.translate(end, state);

        JsonNode snapshot = state.partsSnapshot();
        assertThat(snapshot).hasSize(1);
        JsonNode part = snapshot.get(0);
        assertThat(part.get("state").asText()).isEqualTo("output-error");
        assertThat(part.has("output")).isFalse();
        assertThat(part.get("errorText").asText()).isEqualTo("fetch_context timed out");
    }
}

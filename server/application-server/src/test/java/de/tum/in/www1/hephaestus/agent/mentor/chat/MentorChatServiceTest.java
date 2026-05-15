package de.tum.in.www1.hephaestus.agent.mentor.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentProperties;
import de.tum.in.www1.hephaestus.agent.mentor.MentorPiAdapter;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.MentorRunnerException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.PiEventToUiChunkTranslator;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.sandbox.ImagePullPolicy;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxException;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.ResourceLimits;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.SecurityProfile;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

/**
 * Orchestration-level coverage for {@link MentorChatService}: wires the real translator + lock +
 * a recording SseEmitter against a fake {@link AttachedSandbox} so we drive the runner stream
 * synchronously and assert the full chunk sequence the webapp receives. Mocks the persistence
 * boundary to avoid pulling in JPA + the DB.
 */
@DisplayName("MentorChatService orchestration")
@MockitoSettings(strictness = Strictness.LENIENT)
class MentorChatServiceTest extends BaseUnitTest {

    private static final long WORKSPACE_ID = 1L;
    private static final long USER_ID = 99L;
    private static final UUID THREAD_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private final ObjectMapper mapper = new ObjectMapper();

    @Mock
    UserRepository userRepository;

    @Mock
    ChatThreadRepository chatThreadRepository;

    @Mock
    AgentConfigRepository agentConfigRepository;

    @Mock
    WorkspaceContextBuilder workspaceContextBuilder;

    @Mock
    MentorPiAdapter mentorPiAdapter;

    @Mock
    InteractiveSandboxService interactiveSandboxService;

    @Mock
    MentorTurnPersistence persistence;

    private MentorTurnLock turnLock;
    private PiEventToUiChunkTranslator translator;
    private ScheduledExecutorService scheduler;
    private ExecutorService turnExec;
    private FakeSandbox sandbox;
    private MentorChatService service;
    private RecordingEmitter emitter;
    private io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() throws Exception {
        turnLock = new MentorTurnLock();
        translator = new PiEventToUiChunkTranslator();
        scheduler = Executors.newSingleThreadScheduledExecutor();
        // Direct executor so the test runs on the caller thread — no race between dispatch and assertion.
        turnExec = directExecutor();
        sandbox = new FakeSandbox();
        emitter = new RecordingEmitter();

        // Package-private constructors on the executor wrappers (see MentorChatExecutorConfig)
        // let us inject deterministic delegates without reflection — no more brittle final-field
        // rewrites that break on every minor refactor.
        MentorChatExecutorConfig.MentorTurnExecutor turnExecutorBean = new MentorChatExecutorConfig.MentorTurnExecutor(
            turnExec
        );
        MentorChatExecutorConfig.MentorRunnerTimeoutScheduler schedulerBean =
            new MentorChatExecutorConfig.MentorRunnerTimeoutScheduler(scheduler);

        meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        // Instance-level LLM config — all three required fields set so resolveLlmConfig takes
        // the instance path and does not need the agentConfigRepository.
        MentorAgentProperties mentorProps = new MentorAgentProperties(
            "test-image",
            "pi-mentor-runner.mjs",
            100_000,
            "",
            LlmProvider.OPENAI,
            CredentialMode.API_KEY,
            "test-key",
            "test-model",
            600,
            ImagePullPolicy.IF_NOT_PRESENT
        );
        service = new MentorChatService(
            userRepository,
            chatThreadRepository,
            agentConfigRepository,
            mentorProps,
            workspaceContextBuilder,
            mentorPiAdapter,
            interactiveSandboxService,
            translator,
            turnLock,
            persistence,
            mapper,
            turnExecutorBean,
            schedulerBean,
            new MentorChatMetrics(meterRegistry)
        );

        // Default happy-path collaborator wiring; individual tests override as needed.
        User user = new User();
        replaceFinalField(user, "id", USER_ID, true);
        user.setLogin("octo");
        when(userRepository.getCurrentUserElseThrow()).thenReturn(user);

        AgentConfig agentConfig = new AgentConfig();
        agentConfig.setEnabled(true);
        when(agentConfigRepository.findByWorkspaceId(eq(WORKSPACE_ID))).thenReturn(List.of(agentConfig));

        Workspace ws = new Workspace();
        ws.setWorkspaceSlug("acme");
        ChatThread thread = new ChatThread();
        thread.setId(THREAD_ID);
        thread.setWorkspace(ws);
        thread.setUser(user);
        when(persistence.ensureThread(eq(WORKSPACE_ID), eq(THREAD_ID), any(), any())).thenReturn(thread);
        when(persistence.persistInFlight(any(), any(), any(), any())).thenAnswer(inv -> {
            UUID assistantId = inv.getArgument(2, UUID.class);
            return new MentorTurnPersistence.TurnPersistenceCookie(
                THREAD_ID,
                UUID.randomUUID(),
                assistantId,
                Instant.now()
            );
        });
        when(workspaceContextBuilder.build(any())).thenReturn(new LinkedHashMap<>());
        when(interactiveSandboxService.attach(any())).thenReturn(sandbox);
        when(mentorPiAdapter.buildSandboxSpec(any(), any(), any(), any())).thenReturn(stubSpec());
        // augmentFinishWithCost passes through unchanged when the mock isn't told otherwise.
        // Without this stub the default Mockito null return would replace the Finish chunk in the
        // happy-path stream — the wire would lose its terminal frame and `turnComplete` would
        // never resolve.
        when(persistence.augmentFinishWithCost(any(UIMessageChunk.Finish.class), any())).thenAnswer(inv ->
            inv.getArgument(0, UIMessageChunk.Finish.class)
        );
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
        turnExec.shutdownNow();
        sandbox.close(Duration.ZERO);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 1. Happy path: chunks in order + assistant persisted via finalise
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("happy path: emits Start → TextStart → TextDelta×3 → TextEnd → FinishStep → Finish")
    void runTurn_happyPath_emitsStartThenChunksThenFinish() throws Exception {
        // Set up the runner-side drive: respond to control calls, then push the Pi event stream.
        scheduleHappyPathResponses(sandbox).run();

        runTurnSync();

        // Sequence: Start (orchestrator), DataMentorStatus, then translator chunks for
        // message_start (Start+StartStep), text deltas (TextStart, TextDelta×3), turn_end
        // (TextEnd + FinishStep), agent_end (Finish).
        List<String> types = emitter.recordedTypes();
        assertThat(types).containsSubsequence(
            "start",
            "data-mentor-status",
            "text-start",
            "text-delta",
            "text-delta",
            "text-delta",
            "text-end",
            "finish-step",
            "finish"
        );
        // No error chunk on the happy path.
        assertThat(types).doesNotContain("error");
        // Persistence completed via finalise (not interrupt).
        verify(persistence).finalise(any(), any(), any(UIMessageChunk.Finish.class));
        verify(persistence, never()).interrupt(any(), any(), any());
        // Lock released — no leaked active keys.
        assertThat(turnLock.activeKeys()).isZero();
        // The production path through MentorChatService must bump the SUCCESS
        // counter — without this assertion the metrics wiring is decoupled from real flow.
        assertOutcomeRecorded(MentorChatMetrics.Outcome.SUCCESS);
        assertThat(meterRegistry.find("mentor.turn.duration").timer().count()).isEqualTo(1L);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. Client disconnect: runner draining, abort sent, finalise still runs
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("client disconnect mid-stream: abort sent to runner; turn finalises normally")
    void runTurn_clientDisconnect_completesNormallyAndAbortsRunner() throws Exception {
        scheduleHappyPathResponses(sandbox).run();
        // 4 sends succeed (Start, DataMentorStatus, then translator's Start + StartStep from
        // message_start), 5th throws IOException. By call 5 the runner client is live, so the
        // abort hook fires. The runner keeps streaming to Finish; persistence.finalise still
        // runs from inside handleEvent — disconnects must NOT be reclassified as turn failures.
        emitter.disconnectAfterCalls = 4;

        runTurnSync();

        // Abort was sent.
        assertThat(sandbox.methodsSent()).contains("abort");
        // No error chunk emitted — disconnects are not surfaced as turn errors.
        assertThat(emitter.recordedTypes()).doesNotContain("error");
        // Persistence ran finalise (not interrupt).
        verify(persistence, atLeastOnce()).finalise(any(), any(), any(UIMessageChunk.Finish.class));
        verify(persistence, never()).interrupt(any(), any(), any());
        // Lock released.
        assertThat(turnLock.activeKeys()).isZero();
        // Disconnect on the EVENT-HANDLER thread (call #5 = mid-stream chunk send) is
        // intentionally swallowed inside handleEvent; the runner keeps draining; the turn
        // completes naturally as SUCCESS even though the wire was already gone. The abort
        // hook fired (verified above). CLIENT_DISCONNECT outcome is reserved for the rare
        // case where the orchestrator's *synchronous* sends fail before the runner attaches —
        // tested separately below.
        assertOutcomeRecorded(MentorChatMetrics.Outcome.SUCCESS);
    }

    @Test
    @DisplayName("client disconnect on sync send: outcome=CLIENT_DISCONNECT (no runner activity)")
    void runTurn_clientDisconnectOnSyncSend_recordsClientDisconnect() throws Exception {
        // The orchestrator's two synchronous sends (Start, DataMentorStatus) happen BEFORE
        // sandbox.attach. If either throws ClientDisconnectedException (e.g. the client
        // already closed the socket between request acceptance and Tomcat dispatch), the
        // catch sets outcome=CLIENT_DISCONNECT and the sandbox is never attached. This is
        // the only path that records that outcome — without this test it would be
        // dead-on-write.
        emitter.disconnectAfterCalls = 1; // call #2 (DataMentorStatus) throws

        runTurnSync();

        try {
            verify(interactiveSandboxService, never()).attach(any());
        } catch (InteractiveSandboxException e) {
            throw new AssertionError(e);
        }
        assertThat(turnLock.activeKeys()).isZero();
        assertOutcomeRecorded(MentorChatMetrics.Outcome.CLIENT_DISCONNECT);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. Runner poisoned (-32002): sandbox evicted, lock released, row interrupted
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("runner poisoned: PI_ERROR causes sandbox.close + persistence.interrupt + 'error' chunk")
    void runTurn_runnerPoisoned_evictsSandbox() throws Exception {
        scheduleRunnerPoisoned(sandbox).run();

        runTurnSync();

        assertThat(emitter.recordedTypes()).contains("error");
        // Poisoned sandboxes are explicitly closed so the next turn rebuilds fresh.
        assertThat(sandbox.closed.get()).isTrue();
        verify(persistence).interrupt(any(), any(), any(Throwable.class));
        verify(persistence, never()).finalise(any(), any(), any());
        assertThat(turnLock.activeKeys()).isZero();
        // Poisoned is a distinct outcome from a generic error — keep the labels separate so
        assertOutcomeRecorded(MentorChatMetrics.Outcome.POISONED);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. In-flight conflict from persistence → 409 chunk; no runner activity
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("in-flight conflict: persistence throws; conflict chunk sent; sandbox never attached")
    void runTurn_inFlightConflict_returns409() {
        when(persistence.persistInFlight(any(), any(), any(), any())).thenThrow(
            new TurnAlreadyInFlightException(THREAD_ID, new RuntimeException("dup"))
        );

        runTurnSync();

        List<String> types = emitter.recordedTypes();
        // After Start, we hit the conflict path: data-mentor-status (conflict) + error.
        assertThat(types).contains("data-mentor-status").contains("error");
        // No sandbox attach attempted — the SDK was never invoked because persistence threw first.
        // (verify via Mockito on interactiveSandboxService.attach)
        try {
            verify(interactiveSandboxService, never()).attach(any());
        } catch (InteractiveSandboxException e) {
            throw new AssertionError(e);
        }
        assertThat(turnLock.activeKeys()).isZero();
        // In-flight conflict tagged distinctly so SLO panels separate "real failure" from
        // "load-shed retry". This persistence-throws path triggers the DB-index outcome —
        // the JVM lock was acquired, so the conflict comes from the durable backstop.
        assertOutcomeRecorded(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT_DB);
    }

    /**
     * Asserts exactly one increment on {@code mentor.turn.completed{outcome=<expected>}} and
     * exactly one increment on {@code mentor.turn.started}. Keeps started/completed in lockstep
     * — if a future refactor lands one without the other, dashboards drift and SLO ratios lie.
     */
    private void assertOutcomeRecorded(MentorChatMetrics.Outcome expected) {
        assertThat(meterRegistry.find("mentor.turn.started").counter().count()).as("mentor.turn.started").isEqualTo(1d);
        assertThat(meterRegistry.find("mentor.turn.completed").tag("outcome", expected.tag()).counter().count())
            .as("mentor.turn.completed{outcome=%s}", expected.tag())
            .isEqualTo(1d);
        // No other outcome got bumped — proves the test asserts the RIGHT branch.
        long otherOutcomes = java.util.Arrays.stream(MentorChatMetrics.Outcome.values())
            .filter(o -> o != expected)
            .mapToLong(o ->
                Math.round(meterRegistry.find("mentor.turn.completed").tag("outcome", o.tag()).counter().count())
            )
            .sum();
        assertThat(otherOutcomes).as("no other outcome counter bumped").isZero();
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. JVM-lock conflict (LOCAL backstop) — distinct outcome from DB conflict
    // ════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("in-flight conflict (LOCAL): JVM lock already held; persistence never invoked")
    void runTurn_inFlightConflict_LOCAL_distinctOutcome() throws Exception {
        // Hold the lock on a separate carrier thread so the service's tryLock-or-409 sees it busy.
        MentorTurnLock.ThreadKey key = new MentorTurnLock.ThreadKey(WORKSPACE_ID, THREAD_ID);
        java.util.concurrent.CountDownLatch holding = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        Thread holder = new Thread(() ->
            turnLock.withLockOr409(key, () -> {
                holding.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return Boolean.TRUE;
            })
        );
        holder.setDaemon(true);
        holder.start();
        assertThat(holding.await(2, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        try {
            runTurnSync();
        } finally {
            release.countDown();
            holder.join(2_000);
        }

        // Persistence MUST NOT be called when the LOCAL lock rejects up front.
        verify(persistence, never()).persistInFlight(any(), any(), any(), any());
        // Distinct outcome metric so SLO dashboards separate same-JVM double-submit from the
        // durable DB backstop (the latter signals JVM-lock leak across replicas).
        assertOutcomeRecorded(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT_LOCAL);
    }

    // ════════════════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════════════════

    /** Run a turn on the same thread as the test (deterministic) and block until the emitter completes. */
    private void runTurnSync() {
        service.start(new MentorChatService.MentorTurnRequest(WORKSPACE_ID, THREAD_ID, "hello mentor"), emitter);
    }

    /** Direct (synchronous) ExecutorService — the test thread runs every task before returning. */
    private static ExecutorService directExecutor() {
        return new java.util.concurrent.AbstractExecutorService() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }

            @Override
            public void shutdown() {}

            @Override
            public List<Runnable> shutdownNow() {
                return List.of();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long t, TimeUnit u) {
                return true;
            }
        };
    }

    private InteractiveSandboxSpec stubSpec() {
        return new InteractiveSandboxSpec(
            UUID.randomUUID(),
            Long.toString(USER_ID),
            Long.toString(WORKSPACE_ID),
            "image:test",
            List.of("node", "runner.mjs"),
            Map.of(),
            null,
            ResourceLimits.DEFAULT,
            SecurityProfile.DEFAULT,
            Map.of(),
            Map.of()
        );
    }

    /**
     * Push protocol responses + a normal Pi event stream onto the sandbox listener as the
     * orchestrator sends each control frame. Used by the happy-path test.
     */
    private Runnable scheduleHappyPathResponses(FakeSandbox sb) {
        return () ->
            sb.onSend = frame -> {
                String method = frame.path("method").asText("");
                long id = frame.path("id").asLong(0);
                switch (method) {
                    case "hello" -> sb.push(jsonRpcResult(id, mapper.createObjectNode().put("protocolVersion", 1)));
                    case "open_thread" -> sb.push(jsonRpcResult(id, mapper.createObjectNode()));
                    case "prompt" -> {
                        // Stream events in lockstep BEFORE acking the prompt — this is what real Pi does.
                        sb.push(
                            event("message_start", node ->
                                node
                                    .putObject("message")
                                    .put("role", "assistant")
                                    .put("model", "claude-3-5-haiku-20241022")
                            )
                        );
                        for (String chunk : List.of("Hel", "lo, ", "world!")) {
                            sb.push(
                                event("message_update", node -> {
                                    ObjectNode ame = node.putObject("assistantMessageEvent");
                                    ame.put("type", "text_delta");
                                    ame.put("contentIndex", 0);
                                    ame.put("delta", chunk);
                                })
                            );
                        }
                        sb.push(event("turn_end", n -> {}));
                        sb.push(event("agent_end", n -> n.putArray("messages")));
                        sb.push(jsonRpcResult(id, mapper.createObjectNode()));
                    }
                    case "abort", "close_thread", "shutdown" -> sb.push(jsonRpcResult(id, mapper.createObjectNode()));
                    default -> {
                        /* ignore */
                    }
                }
            };
    }

    private Runnable scheduleRunnerPoisoned(FakeSandbox sb) {
        return () ->
            sb.onSend = frame -> {
                String method = frame.path("method").asText("");
                long id = frame.path("id").asLong(0);
                switch (method) {
                    case "hello" -> sb.push(jsonRpcResult(id, mapper.createObjectNode().put("protocolVersion", 1)));
                    case "open_thread" -> sb.push(jsonRpcResult(id, mapper.createObjectNode()));
                    case "prompt" -> {
                        // Runner returns the poisoning PI_ERROR — orchestrator must close the sandbox.
                        ObjectNode error = mapper.createObjectNode();
                        error.put("jsonrpc", "2.0");
                        error.put("id", id);
                        ObjectNode err = error.putObject("error");
                        err.put("code", MentorRunnerException.CODE_PI_ERROR);
                        err.put("message", "pi went sideways");
                        sb.push(error);
                    }
                    case "close_thread", "shutdown" -> sb.push(jsonRpcResult(id, mapper.createObjectNode()));
                    default -> {
                        /* ignore */
                    }
                }
            };
    }

    private ObjectNode jsonRpcResult(long id, JsonNode result) {
        ObjectNode out = mapper.createObjectNode();
        out.put("jsonrpc", "2.0");
        out.put("id", id);
        out.set("result", result);
        return out;
    }

    private ObjectNode event(String type, java.util.function.Consumer<ObjectNode> filler) {
        ObjectNode frame = mapper.createObjectNode();
        frame.put("jsonrpc", "2.0");
        frame.put("method", "event");
        ObjectNode params = frame.putObject("params");
        ObjectNode evt = params.putObject("event");
        evt.put("type", type);
        filler.accept(evt);
        return frame;
    }

    /**
     * Reflection set used ONLY for the JPA-entity {@code User.id} (no setter and we don't want a
     * Spring test slice here). Executor-bean wrappers now take an explicit constructor parameter
     * so we don't reach for reflection there.
     */
    private static void replaceFinalField(Object target, String name, Object value, boolean searchSuper)
        throws Exception {
        Class<?> cls = target.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                if (!searchSuper) throw e;
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " on " + target.getClass());
    }

    // ════════════════════════════════════════════════════════════════════════
    // Recording SseEmitter — captures every chunk for assertion
    // ════════════════════════════════════════════════════════════════════════

    static final class RecordingEmitter extends SseEmitter {

        final List<String> rawData = new CopyOnWriteArrayList<>();
        volatile boolean clientGone = false;
        /** Throw {@link IOException} after this many successful sends (0 = throw immediately). */
        volatile int disconnectAfterCalls = -1;
        private int sendCount = 0;

        RecordingEmitter() {
            super(60_000L);
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (clientGone) {
                throw new IOException("client gone (simulated)");
            }
            if (disconnectAfterCalls >= 0 && sendCount >= disconnectAfterCalls) {
                clientGone = true;
                throw new IOException("client gone (simulated after " + disconnectAfterCalls + " sends)");
            }
            sendCount++;
            // We don't have a Spring response; pull the data out of the builder by serialising
            // the events. SseEmitter.SseEventBuilder.build() returns a Set<DataWithMediaType>;
            // each element's getData() is the raw payload (string or chunk). We collect strings.
            for (ResponseBodyEmitter.DataWithMediaType d : builder.build()) {
                Object data = d.getData();
                if (data instanceof String s) {
                    rawData.add(s);
                }
            }
        }

        @Override
        public void complete() {
            // No-op — we don't drive a real response here.
        }

        List<String> recordedTypes() {
            ObjectMapper m = new ObjectMapper();
            List<String> types = new ArrayList<>(rawData.size());
            for (String raw : rawData) {
                try {
                    JsonNode n = m.readTree(raw);
                    if (n.has("type")) types.add(n.get("type").asText());
                } catch (Exception ignored) {
                    // Not a JSON SSE frame (e.g. a heartbeat comment) — skip.
                }
            }
            return types;
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Fake AttachedSandbox — buffers sent frames, dispatches pushed frames to all listeners
    // ════════════════════════════════════════════════════════════════════════

    static final class FakeSandbox implements AttachedSandbox {

        private final UUID sessionId = UUID.randomUUID();
        private final LinkedBlockingDeque<JsonNode> sent = new LinkedBlockingDeque<>();
        private final CopyOnWriteArrayList<Consumer<JsonNode>> listeners = new CopyOnWriteArrayList<>();
        final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean(false);

        /** Called on every send — installed by the test driver to script responses. */
        volatile Consumer<JsonNode> onSend = f -> {};

        @Override
        public UUID sessionId() {
            return sessionId;
        }

        @Override
        public String userId() {
            return Long.toString(USER_ID);
        }

        @Override
        public String workspaceId() {
            return Long.toString(WORKSPACE_ID);
        }

        @Override
        public void send(JsonNode frame) {
            sent.add(frame);
            Consumer<JsonNode> drv = onSend;
            if (drv != null) drv.accept(frame);
        }

        @Override
        public Disposable subscribe(Consumer<JsonNode> listener) {
            listeners.add(listener);
            return () -> listeners.remove(listener);
        }

        @Override
        public Instant lastActivityAt() {
            return Instant.now();
        }

        @Override
        public Duration idleFor() {
            return Duration.ZERO;
        }

        @Override
        public void close(Duration graceTimeout) {
            closed.set(true);
            listeners.clear();
        }

        void push(JsonNode frame) {
            for (Consumer<JsonNode> l : new ArrayList<>(listeners)) {
                l.accept(frame);
            }
        }

        /** Collected `method` names of every frame the orchestrator sent — for verifying abort etc. */
        List<String> methodsSent() {
            List<String> out = new ArrayList<>();
            for (JsonNode frame : sent) {
                if (frame.has("method")) out.add(frame.get("method").asText());
            }
            return out;
        }
    }
}

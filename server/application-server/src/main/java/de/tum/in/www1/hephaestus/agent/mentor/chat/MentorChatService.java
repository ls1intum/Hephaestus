package de.tum.in.www1.hephaestus.agent.mentor.chat;

import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest;
import de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.in.www1.hephaestus.agent.context.providers.mentor.MentorAspects;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentProperties;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentRequest;
import de.tum.in.www1.hephaestus.agent.mentor.MentorLlmConfig;
import de.tum.in.www1.hephaestus.agent.mentor.MentorPiAdapter;
import de.tum.in.www1.hephaestus.agent.mentor.SessionRestore;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.ClientDisconnectedException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.MentorRunnerException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.TurnAlreadyInFlightException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.PiEventToUiChunkTranslator;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.TranslatorState;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.AttachedSandbox;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxService;
import de.tum.in.www1.hephaestus.agent.sandbox.spi.InteractiveSandboxSpec;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.gitprovider.user.UserRepository;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import de.tum.in.www1.hephaestus.mentor.ChatThreadRepository;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * Runs one mentor chat turn: persist → attach sandbox → handshake → translate runner events
 * into {@link UIMessageChunk}s on the SSE stream. {@link #start} returns once the turn is
 * submitted to the virtual-thread executor; all blocking work happens off the request thread.
 */
@Service
@ConditionalOnProperty(prefix = "hephaestus.sandbox", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class MentorChatService {

    private static final Logger log = LoggerFactory.getLogger(MentorChatService.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final UserRepository userRepository;
    private final ChatThreadRepository chatThreadRepository;
    private final AgentConfigRepository agentConfigRepository;
    private final MentorAgentProperties mentorAgentProperties;
    private final WorkspaceContextBuilder workspaceContextBuilder;
    private final MentorPiAdapter mentorPiAdapter;
    private final InteractiveSandboxService interactiveSandboxService;
    private final PiEventToUiChunkTranslator translator;
    private final MentorTurnLock turnLock;
    private final MentorTurnPersistence persistence;
    private final ObjectMapper objectMapper;
    private final MentorChatExecutorConfig.MentorTurnExecutor turnExecutor;
    private final MentorChatExecutorConfig.MentorRunnerTimeoutScheduler runnerTimeoutScheduler;
    private final MentorChatMetrics metrics;

    /**
     * Submit a turn to the virtual-thread executor and return. {@code clientHolder} lets the
     * SSE disconnect hook abort Pi even when the runner client is attached after bindLifecycle
     * (which fires synchronously) — {@code session.abort()} is documented idempotent.
     */
    public void start(MentorTurnRequest request, SseEmitter emitter) {
        MentorSseChannel channel = new MentorSseChannel(emitter, objectMapper, runnerTimeoutScheduler.scheduler());
        channel.bindLifecycle();
        AtomicReference<MentorRunnerClient> clientHolder = new AtomicReference<>();
        channel.onDisconnect(() -> abortRunnerOnDisconnect(clientHolder.get(), request.threadId()));

        // Record-started fires here so started/completed balance on the executor-rejected branch.
        metrics.recordStarted();
        ExecutorService executor = turnExecutor.executor();
        try {
            executor.execute(() -> dispatchTurn(request, channel, clientHolder));
        } catch (RejectedExecutionException rejected) {
            log.warn("Mentor turn rejected by executor (probably shutting down): {}", rejected.getMessage());
            metrics.recordCompleted(MentorChatMetrics.Outcome.REJECTED);
            channel.completeWithError("Mentor service is shutting down — please retry shortly.");
        }
    }

    private void dispatchTurn(
        MentorTurnRequest request,
        MentorSseChannel channel,
        AtomicReference<MentorRunnerClient> clientHolder
    ) {
        MentorTurnLock.ThreadKey key = new MentorTurnLock.ThreadKey(request.workspaceId(), request.threadId());
        // Outer catch: anything that escapes the lock helper itself (not the lambda) would leave
        // started/completed metrics unbalanced and the emitter dangling for EMITTER_TIMEOUT_MS.
        try {
            Optional<Boolean> acquired = turnLock.withLockOr409(key, () -> {
                Timer.Sample sample = metrics.startTimer();
                try {
                    MentorChatMetrics.Outcome outcome = runTurn(request, channel, clientHolder);
                    metrics.recordCompleted(outcome);
                    return Boolean.TRUE;
                } catch (TurnAlreadyInFlightException dup) {
                    // WARN (not INFO): DB index trip means the JVM lock missed — pageable signal.
                    log.warn("Mentor turn rejected (DB in-flight index): {}", dup.getMessage());
                    metrics.recordCompleted(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT_DB);
                    channel.completeWithConflict();
                    return Boolean.FALSE;
                } catch (RuntimeException e) {
                    log.warn(
                        "Mentor turn failed: workspaceId={}, threadId={}: {}",
                        key.workspaceId(),
                        key.threadId(),
                        e.getMessage(),
                        e
                    );
                    metrics.recordCompleted(MentorChatMetrics.Outcome.ERROR);
                    channel.completeWithError(userFacingError(e));
                    return Boolean.FALSE;
                } finally {
                    metrics.stopTimer(sample);
                }
            });
            if (acquired.isEmpty()) {
                log.info(
                    "Mentor turn rejected (in flight): workspaceId={}, threadId={}",
                    key.workspaceId(),
                    key.threadId()
                );
                metrics.recordCompleted(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT_LOCAL);
                channel.completeWithConflict();
            }
        } catch (Throwable t) {
            log.error(
                "Mentor dispatchTurn escaped: workspaceId={}, threadId={}: {}",
                key.workspaceId(),
                key.threadId(),
                t.getMessage(),
                t
            );
            metrics.recordCompleted(MentorChatMetrics.Outcome.ERROR);
            try {
                channel.completeWithError("Mentor turn failed unexpectedly.");
            } catch (RuntimeException ignored) {
                // Best-effort: the channel may already be closed.
            }
            // Re-throw Error subclasses (OOME, StackOverflowError) — JVM stability over metrics tidy-up.
            if (t instanceof Error err) throw err;
        }
    }

    private MentorChatMetrics.Outcome runTurn(
        MentorTurnRequest request,
        MentorSseChannel channel,
        AtomicReference<MentorRunnerClient> clientHolder
    ) {
        // Push thread + workspace ids into MDC so every WARN/ERROR in this turn carries the
        // correlation keys. Cleared in `finally` so the v-thread pool doesn't leak context.
        org.slf4j.MDC.put("mentorThreadId", request.threadId().toString());
        org.slf4j.MDC.put("mentorWorkspaceId", Long.toString(request.workspaceId()));
        try {
            return runTurnInternal(request, channel, clientHolder);
        } finally {
            org.slf4j.MDC.remove("mentorThreadId");
            org.slf4j.MDC.remove("mentorWorkspaceId");
        }
    }

    private MentorChatMetrics.Outcome runTurnInternal(
        MentorTurnRequest request,
        MentorSseChannel channel,
        AtomicReference<MentorRunnerClient> clientHolder
    ) {
        User user = userRepository.getCurrentUserElseThrow();
        ChatThread thread = persistence.ensureThread(
            request.workspaceId(),
            request.threadId(),
            user,
            request.userMessage()
        );
        MentorLlmConfig llmConfig = resolveLlmConfig(request.workspaceId());
        Optional<byte[]> priorSessionBytes = chatThreadRepository.findSessionJsonl(thread.getId());

        UUID assistantMessageId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            request.userMessage(),
            assistantMessageId,
            request.clientUserMessageId()
        );
        TranslatorState state = new TranslatorState(assistantMessageId);

        AttachedSandbox sandbox = null;
        MentorRunnerClient client = null;
        CompletableFuture<Void> turnComplete = new CompletableFuture<>();
        java.util.concurrent.atomic.AtomicBoolean errorChunkSeen = new java.util.concurrent.atomic.AtomicBoolean();
        channel.startHeartbeat();
        boolean poisoned = false;
        MentorChatMetrics.Outcome outcome = MentorChatMetrics.Outcome.ERROR;
        try {
            // Emit Start (with assistantMessageId) BEFORE sandbox.attach so the AI-SDK
            // reducer creates the placeholder message immediately — sandbox cold start can
            // take several seconds and the user otherwise stares at a blank screen. Mark the
            // translator started here so it suppresses the duplicate Start it would otherwise
            // emit on Pi's first message_start; the translator still fires StartStep per
            // assistant message (translator now decouples the two — see handleMessageStart).
            channel.send(new UIMessageChunk.Start(assistantMessageId, null));
            state.markStarted();
            channel.send(UIMessageChunk.DataMentorStatus.of("warming-up", "container-cold"));

            Map<String, byte[]> aspectInputs = buildAspectContext(request, user);
            SessionRestore sessionRestore = priorSessionBytes
                .filter(bytes -> bytes.length > 0)
                .map(bytes -> new SessionRestore(request.threadId(), bytes))
                .orElse(null);
            InteractiveSandboxSpec spec = mentorPiAdapter.buildSandboxSpec(
                new MentorAgentRequest(request.workspaceId(), user.getId()),
                llmConfig,
                aspectInputs,
                sessionRestore
            );
            sandbox = interactiveSandboxService.attach(spec);

            // If the client disconnected during the (potentially seconds-long) cold-start
            // attach, short-circuit BEFORE wiring up the runner subscription + 20s hello
            // deadline. Without this, a dead client would hold an entire turn for the full
            // hello timeout. The outer ClientDisconnectedException catch closes the channel
            // and runs the finally — sandbox/client get cleaned up there.
            if (channel.isClientGone()) {
                throw new ClientDisconnectedException("Client disconnected during sandbox attach");
            }

            client = new MentorRunnerClient(
                sandbox,
                objectMapper,
                event -> handleEvent(event, state, channel, cookie, turnComplete, errorChunkSeen),
                callback -> handleFetchContext(callback, aspectInputs),
                runnerTimeoutScheduler.scheduler(),
                // Per-thread event filter: the sandbox is shared by (userId, workspaceId), so
                // a second tab in the same workspace would otherwise see this tab's events.
                request.threadId()
            );
            // Publish to the disconnect hook BEFORE start(): if start() throws (rare — frame
            // queue full, listener exception on the very first emit) the hook still finds the
            // client and aborts cleanly. The hook's abort is idempotent on Pi's side.
            clientHolder.set(client);
            client.start();
            // SSE lifecycle may have flipped the channel between bindLifecycle and clientHolder.set.
            // Re-fire the abort if so.
            if (channel.isClientGone()) {
                abortRunnerOnDisconnect(client, request.threadId());
            }

            JsonNode hello = client.hello().get(20, TimeUnit.SECONDS);
            verifyProtocol(hello);

            // Per-sandbox FIFO serialisation: Pi's AgentSessionRuntime is single-session.
            // `runtime.switchSession(...)` (fired by every `open_thread`) unsubscribes the
            // prior session — so if tab-B sends open_thread while tab-A is mid-prompt on the
            // same (userId, workspaceId) sandbox, tab-A's in-flight LLM stream is orphaned.
            // Serialise the open_thread → terminal-chunk window per sandbox to make tab-B
            // wait for tab-A to finish. Different-user / different-workspace turns are
            // unaffected (different SandboxKey). The per-thread lock above remains as the
            // outer single-flight guard (concurrent SAME-thread attempts return 409).
            MentorTurnLock.SandboxKey sandboxKey = new MentorTurnLock.SandboxKey(request.workspaceId(), user.getId());
            try (var ignored = turnLock.acquireSandboxLock(sandboxKey)) {
                client.openThread(request.threadId()).get(10, TimeUnit.SECONDS);
                client
                    .prompt(request.threadId(), request.userMessage())
                    .whenComplete((result, ex) -> {
                        if (ex != null && !turnComplete.isDone()) {
                            turnComplete.completeExceptionally(ex);
                        }
                    });

                turnComplete.get(MentorRunnerClient.DEFAULT_PROMPT_TIMEOUT.toMillis() + 30_000, TimeUnit.MILLISECONDS);
            }
            // Natural-finish path: Pi emitted `agent_end` → handleEvent sent the `finish` chunk
            // already. Close the wire with AI-SDK's `[DONE]` sentinel.
            channel.completeWithDone();
            // If an Error chunk reached the wire mid-turn, the DB row is `interrupted`. Avoid
            // recording SUCCESS in that case — metrics must agree with persistence.
            outcome = errorChunkSeen.get() ? MentorChatMetrics.Outcome.ERROR : MentorChatMetrics.Outcome.SUCCESS;
        } catch (TimeoutException timeout) {
            // Turn outlasted the prompt deadline (165s) + 30s grace; the future never resolved.
            // Persistence sees an interrupted assistant row; the runner watchdog is what
            // actually reclaims the Pi session.
            log.warn(
                "Mentor turn timed out waiting for agent_end (threadId={}): {}",
                request.threadId(),
                timeout.toString()
            );
            persistence.interrupt(cookie, state, timeout);
            channel.completeWithError("Mentor turn timed out before completion.");
            outcome = MentorChatMetrics.Outcome.TIMEOUT;
        } catch (ClientDisconnectedException disconnect) {
            // Browser closed mid-turn (tab close, refresh, network blip). This is NOT a turn
            // failure: the runner subscription keeps draining and `handleEvent` will still call
            // `persistence.finalise(...)` (or `interrupt(...)` on a runner-side error) when the
            // terminal Finish/Error chunk arrives. Do not poison the sandbox, do not interrupt
            // the row. The abort-Pi hook fired via the channel — no manual abort needed.
            log.info(
                "Mentor client disconnected mid-turn; runner draining to natural finish: {}",
                disconnect.getMessage()
            );
            try {
                turnComplete.get(20, TimeUnit.SECONDS);
            } catch (Exception drainEx) {
                log.debug("Drain after client disconnect timed out / errored: {}", drainEx.toString());
                if (!turnComplete.isDone()) {
                    persistence.interrupt(cookie, state, disconnect);
                }
            }
            outcome = MentorChatMetrics.Outcome.CLIENT_DISCONNECT;
        } catch (Exception e) {
            poisoned = isPoisoning(e);
            log.warn("Mentor turn errored (poisoned={}): {}", poisoned, e.getMessage(), e);
            persistence.interrupt(cookie, state, e);
            channel.completeWithError(userFacingError(e));
            outcome = poisoned ? MentorChatMetrics.Outcome.POISONED : MentorChatMetrics.Outcome.ERROR;
        } finally {
            channel.close();
            // Skip closeThread on client-disconnect: the runner already received `abort` via
            // the channel's disconnect hook and will free its slot when its watchdog fires.
            // Issuing closeThread here would force a sandbox round-trip and the .join() can
            // chain on top of the 20s drain timeout — under load that lets a single dead
            // client hold the per-thread lock for tens of seconds.
            boolean skipCloseThread = outcome == MentorChatMetrics.Outcome.CLIENT_DISCONNECT;
            if (client != null) {
                if (!skipCloseThread) {
                    try {
                        client
                            .closeThread(request.threadId())
                            .orTimeout(5, TimeUnit.SECONDS)
                            .exceptionally(ex -> null)
                            .join();
                    } catch (RuntimeException ex) {
                        log.debug("close_thread cleanup failed: {}", ex.toString());
                    }
                }
                // Always release the runner-client resources, even on the disconnect path —
                // the client owns timers / subscriptions independent of the thread session.
                client.close();
            }
            if (poisoned && sandbox != null) {
                // Pi state is corrupt — force the registry to drop the sandbox so the next turn
                // starts from a clean container instead of inheriting bad state.
                try {
                    sandbox.close(Duration.ofSeconds(5));
                } catch (RuntimeException ex) {
                    log.warn("Failed to terminate poisoned mentor sandbox: {}", ex.toString());
                }
            }
        }
        return outcome;
    }

    /**
     * Ask the runner to stop generating tokens because the client is gone. The cost of tokens
     * already in flight is still charged (Pi can't unsend the LLM request), but no further
     * generation happens. {@code session.abort()} is documented idempotent — calling it after
     * the turn has naturally completed is harmless.
     */
    private static void abortRunnerOnDisconnect(@Nullable MentorRunnerClient client, UUID threadId) {
        if (client == null) return;
        try {
            client
                .abort(threadId)
                .orTimeout(2, TimeUnit.SECONDS)
                .exceptionally(ex -> null)
                .join();
        } catch (Exception ignored) {
            // Best-effort; the runner watchdog will fire if it really wedged.
        }
    }

    /** Bound the cause-chain walk so a self-cycle (rare but seen in JDK 21 with virtual threads) doesn't infinite-loop. */
    private static final int MAX_CAUSE_DEPTH = 32;

    private static boolean isPoisoning(Throwable e) {
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth++ < MAX_CAUSE_DEPTH) {
            if (cur instanceof MentorRunnerException mre && mre.poisonsSandbox()) {
                return true;
            }
            Throwable next = cur.getCause();
            if (next == cur) break; // self-cycle guard
            cur = next;
        }
        return false;
    }

    private void handleEvent(
        JsonNode piEvent,
        TranslatorState state,
        MentorSseChannel channel,
        MentorTurnPersistence.TurnPersistenceCookie cookie,
        CompletableFuture<Void> turnComplete,
        java.util.concurrent.atomic.AtomicBoolean errorChunkSeen
    ) {
        try {
            List<UIMessageChunk> chunks = translator.translate(piEvent, state);
            for (UIMessageChunk chunk : chunks) {
                // Defensive: once the turn is done, drop any trailing chunks. Pi shouldn't emit
                // anything past agent_end, but a misbehaving runner / late delivery would
                // otherwise hit a closed emitter and re-trigger finalise on an already-finalised row.
                if (turnComplete.isDone()) break;
                if (chunk instanceof UIMessageChunk.Finish finish) {
                    UIMessageChunk.Finish toSend = finish;
                    try {
                        toSend = persistence.augmentFinishWithCost(finish, state);
                    } catch (RuntimeException costEx) {
                        // DEBUG, not WARN: a missing price row is observable via the
                        // `mentor_cost_recorded_ratio` gauge already; per-turn WARN under
                        // sustained pricing-table drift would noise out actionable logs.
                        log.debug("Cost augmentation failed — sending raw Finish: {}", costEx.toString());
                    }
                    channel.send(toSend);
                    persistence.finalise(cookie, state, toSend);
                    Double costUsd = toSend.messageMetadata() != null ? toSend.messageMetadata().costUsd() : null;
                    if (costUsd != null) metrics.recordCostUsd(costUsd);
                    turnComplete.complete(null);
                } else if (chunk instanceof UIMessageChunk.Error err) {
                    // Persistence sees `interrupted`; the wire already carries the Error chunk.
                    // Failing the future exceptionally would re-emit a generic Error + [DONE]
                    // (double-error on the wire) AND let the outer catch call interrupt again.
                    // Complete normally and let runTurnInternal observe the interrupted row via
                    // the Error-chunk-seen flag below to record the correct outcome metric.
                    channel.send(chunk);
                    persistence.interrupt(cookie, state, new IllegalStateException(err.errorText()));
                    errorChunkSeen.set(true);
                    turnComplete.complete(null);
                } else {
                    channel.send(chunk);
                }
            }
        } catch (ClientDisconnectedException disconnect) {
            // Client gone but the runner subscription stays alive so persistence.finalise runs
            // when Pi emits Finish. Do NOT fail turnComplete — let the natural terminal chunk
            // close it. The outer ClientDisconnectedException catch in runTurn is for the
            // SYNCHRONOUS send paths (Start, DataMentorStatus); inside the runner-event handler
            // we only need to stop writing.
            log.debug(
                "SSE send failed inside event handler (clientGone={}): {}",
                channel.isClientGone(),
                disconnect.toString()
            );
        } catch (RuntimeException e) {
            log.warn("Event translation/send failed: {}", e.getMessage(), e);
            if (!turnComplete.isDone()) {
                turnComplete.completeExceptionally(e);
            }
        }
    }

    private static void verifyProtocol(JsonNode hello) {
        if (
            hello == null ||
            !hello.has("protocolVersion") ||
            hello.get("protocolVersion").asInt(0) != MentorRunnerClient.PROTOCOL_VERSION
        ) {
            // Bound the message: a misbehaving runner could ship a 10MB hello frame and
            // hello.get("protocolVersion") might be an unbounded JsonNode whose toString()
            // would bloat the log line and (worse) flow into the user-facing error chunk.
            JsonNode v = hello != null ? hello.get("protocolVersion") : null;
            String got = v == null ? "missing" : (v.isIntegralNumber() ? Integer.toString(v.asInt()) : "non-integer");
            throw new IllegalStateException(
                "Runner protocol mismatch — expected version " + MentorRunnerClient.PROTOCOL_VERSION + ", got " + got
            );
        }
        // Fail-closed against PROTOCOL_ONLY drift: in stub mode the runner stubs every prompt,
        // so a deploy that accidentally inherits MENTOR_RUNNER_PROTOCOL_ONLY=1 would silently
        // serve canned answers to every user. The runner advertises the flag on hello.
        if (hello.path("protocolOnly").asBoolean(false)) {
            throw new IllegalStateException(
                "Runner started in MENTOR_RUNNER_PROTOCOL_ONLY=1 — refusing to serve traffic"
            );
        }
    }

    /**
     * Map an unchecked exception to a string the user can see in the chat. The raw
     * {@code e.getMessage()} can leak workspace ids, exception class names, internal stack
     * details ({@code "No LLM config for mentor in workspace 42"},
     * {@code "runner error -32002: <internal>"}); the wire ends up as a chat-error toast in
     * the webapp without any further filtering, so the controller is the right boundary.
     * Raw message stays in the WARN log for ops.
     */
    private static String userFacingError(Throwable e) {
        if (e instanceof MentorRunnerException) {
            return "Mentor service hit an unexpected error — please retry.";
        }
        if (e instanceof java.util.concurrent.TimeoutException) {
            return "Mentor turn timed out before completion.";
        }
        if (e instanceof ClientDisconnectedException) {
            // Should never surface to a still-connected client, but guard anyway.
            return "Connection lost.";
        }
        return "Mentor turn failed unexpectedly.";
    }

    private Map<String, byte[]> buildAspectContext(MentorTurnRequest request, User user) {
        return workspaceContextBuilder.build(
            new ContextRequest.MentorChatRequest(request.workspaceId(), user.getId(), request.threadId())
        );
    }

    /**
     * Resolve the LLM config mentor should use for this turn.
     *
     * <p>Primary path: all three required fields on {@code MentorAgentProperties} are set
     * ({@code llmProvider}, {@code credentialMode}, {@code modelName}) — the instance config
     * applies to every workspace without a DB row. Set these in {@code application-local.yml}
     * or via env vars for local dev and single-model deployments.
     *
     * <p>Fallback: first enabled {@code AgentConfig} for the workspace — the original
     * multi-tenant path for per-workspace key routing.
     */
    private MentorLlmConfig resolveLlmConfig(long workspaceId) {
        if (
            mentorAgentProperties.llmProvider() != null &&
            mentorAgentProperties.credentialMode() != null &&
            mentorAgentProperties.modelName() != null
        ) {
            return MentorLlmConfig.fromProperties(mentorAgentProperties);
        }
        return agentConfigRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(AgentConfig::isEnabled)
            .findFirst()
            .map(MentorLlmConfig::fromAgentConfig)
            .orElseThrow(() ->
                new IllegalStateException(
                    "No LLM config for mentor in workspace " +
                        workspaceId +
                        " — set hephaestus.mentor.agent.llm-provider / credential-mode / model-name" +
                        " or create an enabled AgentConfig for this workspace"
                )
            );
    }

    private JsonNode handleFetchContext(MentorRunnerClient.FetchContextRequest req, Map<String, byte[]> aspectInputs) {
        String path = req.path();
        String key = path.startsWith(MentorPiAdapter.ASPECT_INPUT_PREFIX)
            ? path
            : MentorPiAdapter.ASPECT_INPUT_PREFIX + stripLeadingPath(path);
        if (!MentorAspects.ALLOWED_OUTPUT_KEYS.contains(key)) {
            throw new IllegalArgumentException("fetch_context path not allowed: " + path);
        }
        byte[] bytes = aspectInputs.get(key);
        if (bytes == null) {
            return NODES.nullNode();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (JacksonException e) {
            // Jackson 3 throws unchecked JacksonException for all parse failures.
            throw new IllegalStateException("Failed to parse aspect JSON for path " + path, e);
        }
    }

    private static String stripLeadingPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /**
     * Service-facing request: extracted from the controller body + workspace context. The
     * optional {@code clientUserMessageId} is the UUID the AI SDK transport minted for the
     * user message — persistence honours it so optimistic UI on the client reconciles.
     */
    public record MentorTurnRequest(
        long workspaceId,
        UUID threadId,
        String userMessage,
        @Nullable UUID clientUserMessageId
    ) {
        public MentorTurnRequest(long workspaceId, UUID threadId, String userMessage) {
            this(workspaceId, threadId, userMessage, null);
        }
    }
}

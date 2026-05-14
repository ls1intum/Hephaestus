package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import de.tum.in.www1.hephaestus.agent.config.AgentConfig;
import de.tum.in.www1.hephaestus.agent.config.AgentConfigRepository;
import de.tum.in.www1.hephaestus.agent.context.ContextRequest;
import de.tum.in.www1.hephaestus.agent.context.WorkspaceContextBuilder;
import de.tum.in.www1.hephaestus.agent.context.providers.mentor.FindingsHistoryAspectProvider;
import de.tum.in.www1.hephaestus.agent.context.providers.mentor.PracticeCatalogAspectProvider;
import de.tum.in.www1.hephaestus.agent.context.providers.mentor.UserAspectProvider;
import de.tum.in.www1.hephaestus.agent.context.providers.mentor.WorkspaceAspectProvider;
import de.tum.in.www1.hephaestus.agent.mentor.MentorAgentRequest;
import de.tum.in.www1.hephaestus.agent.mentor.MentorPiAdapter;
import de.tum.in.www1.hephaestus.agent.mentor.MentorReplayMessage;
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
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

/**
 * Runs one mentor chat turn: persist → attach sandbox → handshake → translate runner events
 * into {@link UIMessageChunk}s on the SSE stream. {@link #start} returns once the turn is
 * submitted to the virtual-thread executor; all blocking work happens off the request thread.
 */
@Service
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true")
@RequiredArgsConstructor
public class MentorChatService {

    private static final Logger log = LoggerFactory.getLogger(MentorChatService.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** Whitelist of allowed {@code fetch_context} aspect keys; full output-key match, no path stripping. */
    private static final Set<String> ALLOWED_FETCH_KEYS = Set.of(
        UserAspectProvider.OUTPUT_KEY,
        WorkspaceAspectProvider.OUTPUT_KEY,
        PracticeCatalogAspectProvider.OUTPUT_KEY,
        FindingsHistoryAspectProvider.OUTPUT_KEY
    );

    private final UserRepository userRepository;
    private final AgentConfigRepository agentConfigRepository;
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
     * Submit a turn to the mentor virtual-thread executor and return immediately. Lifecycle
     * callbacks on {@code emitter} flip a per-turn "client gone" flag so the runner subscription
     * keeps draining to completion (for cost accounting) but no further SSE writes are attempted.
     *
     * <p>The shared {@code clientHolder} lets the SSE lifecycle (which fires on the request
     * thread, possibly before the runner is even attached) ask Pi to stop generating once the
     * client is gone. {@code session.abort()} is documented idempotent; calling it after the
     * turn has naturally completed is harmless.
     */
    public void start(MentorTurnRequest request, SseEmitter emitter) {
        // SecurityContext propagates to the vthread executor via
        // {@link MentorChatExecutorConfig.MentorTurnExecutor}'s
        // {@link DelegatingSecurityContextExecutorService} wrapper, so
        // {@code userRepository.getCurrentUserElseThrow()} called inside {@code runTurn} sees
        // the same authentication the controller's @PreAuthorize verified. No explicit
        // capture-and-pass needed.
        MentorSseChannel channel = new MentorSseChannel(emitter, objectMapper, runnerTimeoutScheduler.scheduler());
        channel.bindLifecycle();
        AtomicReference<MentorRunnerClient> clientHolder = new AtomicReference<>();
        // Hook captures `clientHolder` because the runner client is attached AFTER bindLifecycle
        // runs (so the lifecycle callbacks can flip the channel before the runner exists).
        // session.abort() is idempotent; if clientHolder is still null when the hook fires
        // (race window), abortRunnerOnDisconnect short-circuits and the natural runtime later
        // attaches a client that will be torn down via runTurn's finally instead.
        channel.onDisconnect(() -> abortRunnerOnDisconnect(clientHolder.get(), request.threadId()));

        // Record-started fires here (not inside the executor task) so the started/completed
        // totals balance even on the RejectedExecutionException branch below.
        metrics.recordStarted();
        ExecutorService executor = turnExecutor.executor();
        try {
            executor.execute(() -> dispatchTurn(request, channel, clientHolder));
        } catch (RejectedExecutionException rejected) {
            // Executor shut down (PreDestroy / context refresh) — we already wrote the
            // SSE response headers, so the only honest move is to send a clean Error chunk
            // plus the AI-SDK [DONE] sentinel before completing the emitter.
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
        // Outer try/catch covers *any* unchecked throwable that can escape `turnLock.withLockOr409`
        // itself (e.g. a future ConcurrentHashMap.compute that throws an OOME or any code path
        // outside the lambda). Without it, an uncaught throwable would leave `started` incremented,
        // `completed` not, and the SseEmitter dangling until EMITTER_TIMEOUT_MS (10 min) fires.
        try {
            Optional<Boolean> acquired = turnLock.withLockOr409(key, () -> {
                Timer.Sample sample = metrics.startTimer();
                try {
                    MentorChatMetrics.Outcome outcome = runTurn(request, channel, clientHolder);
                    metrics.recordCompleted(outcome);
                    return Boolean.TRUE;
                } catch (TurnAlreadyInFlightException dup) {
                    log.info("Mentor turn rejected (DB in-flight index): {}", dup.getMessage());
                    metrics.recordCompleted(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT);
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
                metrics.recordCompleted(MentorChatMetrics.Outcome.IN_FLIGHT_CONFLICT);
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
        // SecurityContext propagates from the request thread via the executor's
        // DelegatingSecurityContextExecutorService wrapper.
        User user = userRepository.getCurrentUserElseThrow();
        ChatThread thread = persistence.ensureThread(
            request.workspaceId(),
            request.threadId(),
            user,
            request.userMessage()
        );
        AgentConfig agentConfig = resolveAgentConfig(request.workspaceId());

        UUID assistantMessageId = UUID.randomUUID();
        MentorTurnPersistence.TurnPersistenceCookie cookie = persistence.persistInFlight(
            thread,
            request.userMessage(),
            assistantMessageId,
            request.clientUserMessageId()
        );
        TranslatorState state = new TranslatorState(assistantMessageId);
        List<MentorReplayMessage> replay = persistence.buildReplay(thread.getId());

        AttachedSandbox sandbox = null;
        MentorRunnerClient client = null;
        CompletableFuture<Void> turnComplete = new CompletableFuture<>();
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
            InteractiveSandboxSpec spec = mentorPiAdapter.buildSandboxSpec(
                new MentorAgentRequest(request.workspaceId(), user.getId()),
                agentConfig,
                aspectInputs
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
                event -> handleEvent(event, state, channel, cookie, turnComplete),
                callback -> handleFetchContext(callback, aspectInputs),
                runnerTimeoutScheduler.scheduler()
            );
            clientHolder.set(client);
            client.start();
            // Tiny race: the SSE lifecycle may have flipped the channel *before* clientHolder
            // captured `client`. In that case the hook's clientHolder.get() returned null and
            // skipped the abort. Re-fire here if so — abort() is idempotent on Pi's side.
            if (channel.isClientGone()) {
                abortRunnerOnDisconnect(client, request.threadId());
            }

            JsonNode hello = client.hello().get(20, TimeUnit.SECONDS);
            verifyProtocol(hello);
            client.openThread(request.threadId()).get(10, TimeUnit.SECONDS);
            client.replayContext(request.threadId(), replay).get(10, TimeUnit.SECONDS);
            client
                .prompt(request.threadId(), request.userMessage())
                .whenComplete((result, ex) -> {
                    if (ex != null && !turnComplete.isDone()) {
                        turnComplete.completeExceptionally(ex);
                    }
                });

            turnComplete.get(MentorRunnerClient.DEFAULT_PROMPT_TIMEOUT.toMillis() + 30_000, TimeUnit.MILLISECONDS);
            // Natural-finish path: Pi emitted `agent_end` → handleEvent sent the `finish` chunk
            // already. Close the wire with AI-SDK's `[DONE]` sentinel.
            channel.completeWithDone();
            outcome = MentorChatMetrics.Outcome.SUCCESS;
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

    private static boolean isPoisoning(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof MentorRunnerException mre && mre.poisonsSandbox()) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    private void handleEvent(
        JsonNode piEvent,
        TranslatorState state,
        MentorSseChannel channel,
        MentorTurnPersistence.TurnPersistenceCookie cookie,
        CompletableFuture<Void> turnComplete
    ) {
        try {
            List<UIMessageChunk> chunks = translator.translate(piEvent, state);
            for (UIMessageChunk chunk : chunks) {
                if (chunk instanceof UIMessageChunk.Finish finish) {
                    // Cost augmentation must not block the wire Finish: a pricing-row miss, DB
                    // blip, or token-math overflow would otherwise propagate up `handleEvent`,
                    // turn-complete the future exceptionally, and surface as an interrupted row
                    // + generic error chunk even though Pi finished cleanly. Send the raw Finish
                    // on failure; cost can be backfilled out-of-band.
                    UIMessageChunk.Finish toSend = finish;
                    try {
                        toSend = persistence.augmentFinishWithCost(finish, state);
                    } catch (RuntimeException costEx) {
                        log.warn(
                            "Cost augmentation failed for Finish chunk — sending raw Finish: {}",
                            costEx.toString()
                        );
                    }
                    channel.send(toSend);
                    persistence.finalise(cookie, state, toSend);
                    Double costUsd = toSend.messageMetadata() != null ? toSend.messageMetadata().costUsd() : null;
                    if (costUsd != null) metrics.recordCostUsd(costUsd);
                    turnComplete.complete(null);
                } else if (chunk instanceof UIMessageChunk.Error err) {
                    channel.send(chunk);
                    persistence.interrupt(cookie, state, new IllegalStateException(err.errorText()));
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
    }

    /**
     * Map an unchecked exception to a string the user can see in the chat. The raw
     * {@code e.getMessage()} can leak workspace ids, exception class names, internal stack
     * details ({@code "No enabled AgentConfig for workspace 42 — mentor cannot start"},
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

    private AgentConfig resolveAgentConfig(long workspaceId) {
        return agentConfigRepository
            .findByWorkspaceId(workspaceId)
            .stream()
            .filter(AgentConfig::isEnabled)
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    "No enabled AgentConfig for workspace " + workspaceId + " — mentor cannot start"
                )
            );
    }

    private JsonNode handleFetchContext(MentorRunnerClient.FetchContextRequest req, Map<String, byte[]> aspectInputs) {
        String path = req.path();
        String key = path.startsWith(MentorPiAdapter.ASPECT_INPUT_PREFIX)
            ? path
            : MentorPiAdapter.ASPECT_INPUT_PREFIX + stripLeadingPath(path);
        if (!ALLOWED_FETCH_KEYS.contains(key)) {
            throw new IllegalArgumentException("fetch_context path not allowed: " + path);
        }
        byte[] bytes = aspectInputs.get(key);
        if (bytes == null) {
            return NODES.nullNode();
        }
        try {
            return objectMapper.readTree(bytes);
        } catch (IOException e) {
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

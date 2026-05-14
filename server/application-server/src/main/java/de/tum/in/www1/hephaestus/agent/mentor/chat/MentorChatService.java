package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
@ConditionalOnProperty(name = "hephaestus.mentor.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class MentorChatService {

    private static final Logger log = LoggerFactory.getLogger(MentorChatService.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    /** Emit an SSE comment-only keep-alive after this many ns of silence. */
    private static final long HEARTBEAT_QUIET_NS = TimeUnit.SECONDS.toNanos(20);

    /** Wake the heartbeat scheduler this often to check {@link #HEARTBEAT_QUIET_NS}. */
    private static final long HEARTBEAT_TICK_MS = 5_000;

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
        AtomicBoolean clientGone = new AtomicBoolean(false);
        AtomicReference<MentorRunnerClient> clientHolder = new AtomicReference<>();
        // Register the "ask Pi to stop generating" hook keyed by this turn's clientGone flag.
        // flagDisconnected() pulls + runs it exactly once across all flip sites (SSE lifecycle,
        // sendChunk failure, runner exception path). Side map keeps the hook plumbing out of
        // the orchestrator's per-turn locals.
        disconnectActionByGone.put(clientGone, () -> abortRunnerOnDisconnect(clientHolder.get(), request.threadId()));
        emitter.onCompletion(() -> flagDisconnected(clientGone));
        emitter.onTimeout(() -> {
            flagDisconnected(clientGone);
            emitter.complete();
        });
        emitter.onError(throwable -> {
            log.debug("SseEmitter error on mentor turn (clientGone): {}", throwable.toString());
            flagDisconnected(clientGone);
        });

        ExecutorService executor = turnExecutor.executor();
        executor.execute(() -> dispatchTurn(request, emitter, clientGone, clientHolder));
    }

    private void dispatchTurn(
        MentorTurnRequest request,
        SseEmitter emitter,
        AtomicBoolean clientGone,
        AtomicReference<MentorRunnerClient> clientHolder
    ) {
        MentorTurnLock.ThreadKey key = new MentorTurnLock.ThreadKey(request.workspaceId(), request.threadId());
        Optional<Boolean> acquired = turnLock.withLockOr409(key, () -> {
            try {
                runTurn(request, emitter, clientGone, clientHolder);
                return Boolean.TRUE;
            } catch (TurnAlreadyInFlightException dup) {
                log.info("Mentor turn rejected (DB in-flight index): {}", dup.getMessage());
                sendConflictAndComplete(emitter, clientGone);
                return Boolean.FALSE;
            } catch (RuntimeException e) {
                log.warn(
                    "Mentor turn failed: workspaceId={}, threadId={}: {}",
                    key.workspaceId(),
                    key.threadId(),
                    e.getMessage(),
                    e
                );
                sendErrorAndComplete(emitter, clientGone, "Mentor turn failed: " + e.getMessage());
                return Boolean.FALSE;
            }
        });
        if (acquired.isEmpty()) {
            log.info(
                "Mentor turn rejected (in flight): workspaceId={}, threadId={}",
                key.workspaceId(),
                key.threadId()
            );
            sendConflictAndComplete(emitter, clientGone);
        }
    }

    private void runTurn(
        MentorTurnRequest request,
        SseEmitter emitter,
        AtomicBoolean clientGone,
        AtomicReference<MentorRunnerClient> clientHolder
    ) {
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
        // Heartbeat: ingress (Traefik) `idleTimeout=300s` would close the stream if Pi goes
        // quiet that long. We emit an SSE comment-only line every 20s of silence so the proxy
        // sees regular traffic. `lastSendNanos` is updated by every sendChunk + every heartbeat.
        AtomicLong lastSendNanos = new AtomicLong(System.nanoTime());
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter, clientGone, lastSendNanos);
        boolean poisoned = false;
        try {
            sendChunk(emitter, new UIMessageChunk.Start(assistantMessageId, null), clientGone, lastSendNanos);
            sendChunk(
                emitter,
                UIMessageChunk.DataMentorStatus.of("warming-up", "container-cold"),
                clientGone,
                lastSendNanos
            );

            Map<String, byte[]> aspectInputs = buildAspectContext(request, user);
            InteractiveSandboxSpec spec = mentorPiAdapter.buildSandboxSpec(
                new MentorAgentRequest(request.workspaceId(), user.getId()),
                agentConfig,
                aspectInputs
            );
            sandbox = interactiveSandboxService.attach(spec);

            client = new MentorRunnerClient(
                sandbox,
                objectMapper,
                event -> handleEvent(event, state, emitter, clientGone, cookie, turnComplete, lastSendNanos),
                callback -> handleFetchContext(callback, aspectInputs),
                runnerTimeoutScheduler.scheduler()
            );
            clientHolder.set(client);
            client.start();
            // Tiny race: the SSE lifecycle may have flipped clientGone *before* clientHolder
            // captured `client`. In that case the hook's clientHolder.get() returned null and
            // skipped the abort. Re-fire here if so — abort() is idempotent on Pi's side.
            if (clientGone.get()) {
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
            // already. Now close the SSE stream with AI SDK's `[DONE]` sentinel so byte-equivalence
            // with vercel/ai canonical output holds (json-to-sse-transform-stream.ts:12-14).
            // Cancel the heartbeat first; otherwise a tick between `[DONE]` and `complete()`
            // surfaces as a spurious IllegalStateException.
            heartbeat.cancel(false);
            sendDoneSentinelAndComplete(emitter);
        } catch (ClientDisconnectedException disconnect) {
            // Browser closed mid-turn (tab close, refresh, network blip). This is NOT a turn
            // failure: the runner subscription keeps draining and `handleEvent` will still call
            // `persistence.finalise(...)` (or `interrupt(...)` on a runner-side error) when the
            // terminal Finish/Error chunk arrives. Do not poison the sandbox, do not interrupt
            // the row. The abort-Pi hook fired via flagDisconnected — no manual abort needed.
            log.info(
                "Mentor client disconnected mid-turn; runner draining to natural finish: {}",
                disconnect.getMessage()
            );
            // Best-effort: wait for the runner to surface its own terminal event so persistence
            // captures real cost/tokens. If it overshoots, the timeout below catches it.
            try {
                turnComplete.get(20, TimeUnit.SECONDS);
            } catch (Exception drainEx) {
                log.debug("Drain after client disconnect timed out / errored: {}", drainEx.toString());
                if (!turnComplete.isDone()) {
                    persistence.interrupt(cookie, state, disconnect);
                }
            }
        } catch (Exception e) {
            poisoned = isPoisoning(e);
            log.warn("Mentor turn errored (poisoned={}): {}", poisoned, e.getMessage(), e);
            persistence.interrupt(cookie, state, e);
            // Cancel heartbeat BEFORE the terminal Error chunk + complete(): a tick firing between
            // emitter.complete() and the finally below would hit a completed emitter and surface as
            // a spurious IllegalStateException in logs (and a clientGone false-positive).
            heartbeat.cancel(false);
            sendErrorAndComplete(
                emitter,
                clientGone,
                e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
            );
        } finally {
            heartbeat.cancel(false);
            if (client != null) {
                try {
                    client
                        .closeThread(request.threadId())
                        .orTimeout(5, TimeUnit.SECONDS)
                        .exceptionally(ex -> null)
                        .join();
                } catch (Exception ignored) {
                    // Best-effort cleanup; the registry will reap the sandbox on idle.
                }
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
        SseEmitter emitter,
        AtomicBoolean clientGone,
        MentorTurnPersistence.TurnPersistenceCookie cookie,
        CompletableFuture<Void> turnComplete,
        AtomicLong lastSendNanos
    ) {
        try {
            List<UIMessageChunk> chunks = translator.translate(piEvent, state);
            for (UIMessageChunk chunk : chunks) {
                if (chunk instanceof UIMessageChunk.Finish finish) {
                    // Compute cost BEFORE sending the Finish chunk so the client sees the same
                    // value the DB persists. If we deferred until persistence.finalise the wire
                    // Finish would carry null costUsd and the client would have to refresh to
                    // discover it. augmentFinishWithCost is pure (no transaction).
                    UIMessageChunk.Finish augmented = persistence.augmentFinishWithCost(finish, state);
                    sendChunk(emitter, augmented, clientGone, lastSendNanos);
                    persistence.finalise(cookie, state, augmented, piEvent);
                    turnComplete.complete(null);
                } else if (chunk instanceof UIMessageChunk.Error err) {
                    sendChunk(emitter, chunk, clientGone, lastSendNanos);
                    persistence.interrupt(cookie, state, new IllegalStateException(err.errorText()));
                    turnComplete.complete(null);
                } else {
                    sendChunk(emitter, chunk, clientGone, lastSendNanos);
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
                clientGone.get(),
                disconnect.toString()
            );
        } catch (RuntimeException e) {
            log.warn("Event translation/send failed: {}", e.getMessage(), e);
            if (!turnComplete.isDone()) {
                turnComplete.completeExceptionally(e);
            }
        }
    }

    private void sendChunk(
        SseEmitter emitter,
        UIMessageChunk chunk,
        AtomicBoolean clientGone,
        @Nullable AtomicLong lastSendNanos
    ) {
        if (clientGone.get()) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(chunk);
            emitter.send(SseEmitter.event().data(payload));
            if (lastSendNanos != null) {
                lastSendNanos.set(System.nanoTime());
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise UIMessageChunk", e);
        } catch (IOException e) {
            // Spring closes the emitter and invokes the onError/onCompletion callbacks; we just
            // flip the persistence-only mode and stop trying to write. flagDisconnected fires
            // the abort hook so we don't keep burning LLM tokens for nobody.
            flagDisconnected(clientGone);
            throw new ClientDisconnectedException("SSE send failed: " + e.getMessage(), e);
        } catch (IllegalStateException ex) {
            // Emitter already complete — same outcome as a socket-side disconnect.
            flagDisconnected(clientGone);
            throw new ClientDisconnectedException("SSE emitter closed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Mark the client gone; if this is the first transition false→true and a {@code Runnable}
     * has been registered on the {@link #disconnectActionByGone} side map, fire it once.
     * Centralises the "first flip wins" semantic so abort-on-disconnect fires at most once even
     * if sendChunk + SSE lifecycle race.
     */
    private void flagDisconnected(AtomicBoolean clientGone) {
        if (clientGone.compareAndSet(false, true)) {
            Runnable hook = disconnectActionByGone.remove(clientGone);
            if (hook != null) {
                try {
                    hook.run();
                } catch (RuntimeException ex) {
                    log.debug("Disconnect hook threw: {}", ex.toString());
                }
            }
        }
    }

    /**
     * Side map of disconnect actions registered per-turn. Keyed by the per-turn
     * {@code clientGone} flag (identity-based), with values being the "ask Pi to stop"
     * runnable. The {@link #flagDisconnected} helper pulls + runs the action exactly once.
     * {@link java.util.WeakHashMap} avoids leaking entries if a turn errors out before
     * registering; {@link java.util.Collections#synchronizedMap} because turns may flip from
     * multiple threads (request thread via SSE callback, executor thread via sendChunk).
     */
    private final java.util.Map<AtomicBoolean, Runnable> disconnectActionByGone = java.util.Collections.synchronizedMap(
        new java.util.WeakHashMap<>()
    );

    /**
     * Schedule an SSE comment-only keep-alive so Traefik's idleTimeout (300s) cannot kill the
     * stream during long Pi LLM calls. Fires every {@link #HEARTBEAT_TICK_MS}; emits only when
     * {@link #HEARTBEAT_QUIET_NS} have elapsed since the last write. Comment-only ({@code :ping})
     * per the SSE spec — webapp's {@code DefaultChatTransport} ignores them.
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter, AtomicBoolean clientGone, AtomicLong lastSendNanos) {
        return runnerTimeoutScheduler
            .scheduler()
            .scheduleAtFixedRate(
                () -> {
                    if (clientGone.get()) return;
                    long quietNs = System.nanoTime() - lastSendNanos.get();
                    if (quietNs < HEARTBEAT_QUIET_NS) return;
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                        lastSendNanos.set(System.nanoTime());
                    } catch (IOException | IllegalStateException ex) {
                        // Same disconnect semantics as sendChunk; flip and stop. Spring's emitter
                        // callbacks fire and unwind the orchestrator naturally.
                        clientGone.set(true);
                    }
                },
                HEARTBEAT_TICK_MS,
                HEARTBEAT_TICK_MS,
                TimeUnit.MILLISECONDS
            );
    }

    private void sendErrorAndComplete(SseEmitter emitter, AtomicBoolean clientGone, String errorText) {
        try {
            sendChunk(emitter, new UIMessageChunk.Error(errorText), clientGone, null);
        } catch (RuntimeException ignored) {
            // Already failed.
        }
        sendDoneSentinelAndComplete(emitter);
    }

    private void sendConflictAndComplete(SseEmitter emitter, AtomicBoolean clientGone) {
        try {
            sendChunk(
                emitter,
                UIMessageChunk.DataMentorStatus.of("conflict", "another turn is in flight for this thread"),
                clientGone,
                null
            );
            sendChunk(
                emitter,
                new UIMessageChunk.Error("Another mentor turn is already in flight for this thread."),
                clientGone,
                null
            );
        } catch (RuntimeException ignored) {
            // Best-effort.
        }
        sendDoneSentinelAndComplete(emitter);
    }

    /**
     * Emit AI SDK's terminal sentinel ({@code data: [DONE]\n\n}) and complete the emitter. The
     * client parser ({@code parseJsonEventStream}) tolerates its absence — but vercel/ai's
     * {@code JsonToSseTransformStream.flush()} (json-to-sse-transform-stream.ts:12-14) always
     * writes it, so any downstream proxy/tool that interprets the stream relies on byte-
     * equivalence with the canonical AI SDK output. Sending it on every terminal path
     * (natural finish, error, conflict, client disconnect) keeps our stream protocol-clean.
     */
    private static void sendDoneSentinelAndComplete(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException | IllegalStateException ignored) {
            // Client already disconnected — the next complete() is a no-op anyway.
        }
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {}
    }

    private static void verifyProtocol(JsonNode hello) {
        if (
            hello == null ||
            !hello.has("protocolVersion") ||
            hello.get("protocolVersion").asInt(0) != MentorRunnerClient.PROTOCOL_VERSION
        ) {
            throw new IllegalStateException(
                "Runner protocol mismatch — expected version " +
                    MentorRunnerClient.PROTOCOL_VERSION +
                    ", got " +
                    (hello != null ? hello.get("protocolVersion") : "null")
            );
        }
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

package de.tum.in.www1.hephaestus.agent.mentor.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.mentor.chat.exception.ClientDisconnectedException;
import de.tum.in.www1.hephaestus.agent.mentor.chat.wire.UIMessageChunk;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Per-turn façade over a single {@link SseEmitter}. Owns everything that writes to the wire —
 * chunk serialisation, comment-only heartbeats, terminal {@code [DONE]} sentinel, and the
 * "client gone" disconnect hook — so {@link MentorChatService} can stay focused on orchestration.
 *
 * <p>Lifecycle: instantiated once per turn inside {@link MentorChatService#start}, garbage-
 * collected when the turn ends. {@link #close()} cancels the heartbeat and is idempotent; the
 * orchestrator calls it from its {@code finally} regardless of how the turn finished.
 *
 * <p>The previous design used a singleton {@code WeakHashMap<AtomicBoolean, Runnable>} side-map
 * keyed by per-turn flags — needed only because there was no per-turn object owning the
 * disconnect hook. With the channel as the owner, the map evaporates: each instance carries
 * one {@link AtomicReference#get()} for the hook, and instance-scope replaces identity-scope.
 */
final class MentorSseChannel implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(MentorSseChannel.class);

    /**
     * Emit an SSE comment-only keep-alive after this many ns of silence. Bounded by the
     * narrowest proxy idle window we ship against — Traefik defaults to 180s but some k8s
     * ingress configs cap at 60s. Worst-case silence is {@code HEARTBEAT_INITIAL_DELAY_MS}
     * (first tick) plus {@code HEARTBEAT_QUIET_NS}, so ≤ 21s.
     */
    private static final long HEARTBEAT_QUIET_NS = TimeUnit.SECONDS.toNanos(20);

    /** Wake the heartbeat scheduler this often to check {@link #HEARTBEAT_QUIET_NS}. */
    private static final long HEARTBEAT_TICK_MS = 5_000;

    /**
     * First scheduler tick happens this many ms after {@link #startHeartbeat()}. Kept small so
     * a context-build cold start doesn't leak a silence window of
     * {@code HEARTBEAT_TICK_MS + HEARTBEAT_QUIET_NS} (~25s) before the first keep-alive.
     */
    private static final long HEARTBEAT_INITIAL_DELAY_MS = 1_000;

    private final SseEmitter emitter;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean clientGone = new AtomicBoolean(false);
    private final AtomicLong lastSendNanos = new AtomicLong(System.nanoTime());
    private final AtomicReference<Runnable> disconnectHook = new AtomicReference<>();
    private volatile ScheduledFuture<?> heartbeat;

    MentorSseChannel(SseEmitter emitter, ObjectMapper objectMapper, ScheduledExecutorService scheduler) {
        this.emitter = emitter;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    /**
     * Wire emitter lifecycle callbacks to flip the disconnect flag once. Must run before the
     * orchestrator hands the emitter to async work — otherwise a fast client-side abort can
     * close the emitter before lifecycle is bound. The orchestrator registers the disconnect
     * hook (which has to capture later state — the runner client — via a holder) AFTER this
     * call so the hook is in place when the first flip races in.
     */
    void bindLifecycle() {
        emitter.onCompletion(this::flagDisconnected);
        emitter.onTimeout(() -> {
            flagDisconnected();
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {}
        });
        emitter.onError(throwable -> {
            log.debug("SseEmitter error on mentor turn: {}", throwable.toString());
            flagDisconnected();
        });
    }

    /**
     * Register the "ask Pi to stop generating" hook. Fires exactly once on the first transition
     * to disconnected. If the channel is already disconnected when the hook arrives, the hook
     * runs immediately on the calling thread — covers the race where the SSE lifecycle flips
     * the flag before the orchestrator attaches the runner.
     */
    void onDisconnect(Runnable hook) {
        if (clientGone.get()) {
            try {
                hook.run();
            } catch (RuntimeException ex) {
                log.debug("Disconnect hook threw (channel already disconnected): {}", ex.toString());
            }
            return;
        }
        disconnectHook.set(hook);
    }

    boolean isClientGone() {
        return clientGone.get();
    }

    /**
     * Schedule the comment-only keep-alive. Comments are SSE-spec lines starting with {@code :}
     * — they keep proxies (Traefik {@code idleTimeout=300s}) from killing the stream during
     * long Pi LLM calls but never reach {@code DefaultChatTransport}'s JSON parser.
     */
    void startHeartbeat() {
        heartbeat = scheduler.scheduleAtFixedRate(
            () -> {
                if (clientGone.get()) return;
                long quietNs = System.nanoTime() - lastSendNanos.get();
                if (quietNs < HEARTBEAT_QUIET_NS) return;
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                    lastSendNanos.set(System.nanoTime());
                } catch (IOException | IllegalStateException ex) {
                    // Same semantics as send(): flip and stop. Spring's emitter callbacks fire and
                    // unwind the orchestrator naturally.
                    flagDisconnected();
                }
            },
            HEARTBEAT_INITIAL_DELAY_MS,
            HEARTBEAT_TICK_MS,
            TimeUnit.MILLISECONDS
        );
    }

    /**
     * Serialise a {@link UIMessageChunk} as one SSE {@code data:} frame. Throws
     * {@link ClientDisconnectedException} if the emitter is closed or the socket is dead so the
     * orchestrator can stop writing without poisoning the runner subscription.
     */
    void send(UIMessageChunk chunk) {
        if (clientGone.get()) return;
        try {
            String payload = objectMapper.writeValueAsString(chunk);
            emitter.send(SseEmitter.event().data(payload));
            lastSendNanos.set(System.nanoTime());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise UIMessageChunk", e);
        } catch (IOException e) {
            flagDisconnected();
            throw new ClientDisconnectedException("SSE send failed: " + e.getMessage(), e);
        } catch (IllegalStateException ex) {
            flagDisconnected();
            throw new ClientDisconnectedException("SSE emitter closed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Natural-finish path: emit the {@code [DONE]} sentinel and complete the emitter. Cancels
     * the heartbeat first so a tick can't race {@code emitter.complete()} and surface as a
     * spurious {@code IllegalStateException}. Idempotent.
     */
    void completeWithDone() {
        cancelHeartbeat();
        try {
            emitter.send(SseEmitter.event().data("[DONE]"));
        } catch (IOException | IllegalStateException ignored) {}
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {}
    }

    /** Error path: best-effort emit an {@link UIMessageChunk.Error} chunk + {@code [DONE]}. */
    void completeWithError(String errorText) {
        cancelHeartbeat();
        try {
            send(new UIMessageChunk.Error(errorText));
        } catch (RuntimeException ignored) {}
        completeWithDone();
    }

    /** 409 conflict path: emit a status hint + an error chunk + {@code [DONE]}. */
    void completeWithConflict() {
        cancelHeartbeat();
        try {
            send(UIMessageChunk.DataMentorStatus.of("conflict", "another turn is in flight for this thread"));
            send(new UIMessageChunk.Error("Another mentor turn is already in flight for this thread."));
        } catch (RuntimeException ignored) {}
        completeWithDone();
    }

    /** Idempotent: cancel heartbeat. The {@code finally} in the orchestrator calls this. */
    @Override
    public void close() {
        cancelHeartbeat();
    }

    private void cancelHeartbeat() {
        ScheduledFuture<?> h = this.heartbeat;
        if (h != null) {
            h.cancel(false);
            this.heartbeat = null;
        }
    }

    private void flagDisconnected() {
        if (clientGone.compareAndSet(false, true)) {
            Runnable hook = disconnectHook.getAndSet(null);
            if (hook != null) {
                try {
                    hook.run();
                } catch (RuntimeException ex) {
                    log.debug("Disconnect hook threw: {}", ex.toString());
                }
            }
        }
    }
}

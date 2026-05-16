package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import reactor.core.Disposable;

/**
 * Live handle to one attached sandbox session: bidirectional JSONL channel plus fan-out and idle
 * bookkeeping. {@link #subscribe} delivers frames on a per-subscriber bounded queue + virtual
 * thread dispatcher (slow listeners drop their own frames, never the pump); the {@link Cursor}
 * argument controls whether the snapshot of buffered frames is replayed before live frames.
 * After termination, {@code send} throws and {@code subscribe} returns a disposed handle.
 */
public interface AttachedSandbox extends AutoCloseable {
    UUID sessionId();

    String userId();

    String workspaceId();

    /**
     * Send a JSON frame to the runner's stdin. Blocks until the write completes or the configured
     * stdin timeout elapses. The bounded writer queue rejects with {@link InteractiveSandboxException}
     * when full — the primary backpressure signal to upstream callers.
     *
     * @throws InteractiveSandboxException if the session is closed, the pipe is broken, the queue
     *     is full, the write times out, or the frame cannot be serialised
     */
    void send(JsonNode frame) throws InteractiveSandboxException;

    /**
     * Subscribe to the frame stream starting at {@code cursor}.
     *
     * <p>{@link Cursor#RING_REPLAY} is for re-attach after a server restart — the existing
     * session's buffered frames replay so the new subscriber can rebuild its view. {@link
     * Cursor#FROM_NOW} is correct for multi-turn reuse of the same sandbox: each new turn
     * subscribes fresh, and replay would terminate the new turn instantly with the prior
     * turn's {@code agent_end}.
     *
     * <p>Equivalent to {@link #subscribe(Cursor, Predicate, Consumer)} with an always-true filter.
     *
     * @param cursor   where in the frame stream to begin
     * @param listener invoked on a dedicated virtual thread per subscriber; may block
     * @return a {@link Disposable} whose {@code dispose()} is idempotent
     */
    default Disposable subscribe(Cursor cursor, Consumer<JsonNode> listener) {
        return subscribe(cursor, frame -> true, listener);
    }

    /**
     * Subscribe with a server-side predicate that decides whether each frame is dispatched.
     *
     * <p>The predicate is evaluated INSIDE the SPI's per-subscriber dispatch (before queue
     * enqueue), so filtered-out frames do not consume subscriber-queue capacity and do not
     * trigger drop counters. This is the correct seam for per-thread (or any logical-multiplex)
     * routing on a sandbox shared by {@code (userId, workspaceId)} — without it, every consumer
     * has to filter post-hoc and the dropped-frame counter fires for frames the consumer was
     * never going to deliver anyway.
     *
     * <p><b>Broadcast contract:</b> mentor/practice runners ship some frames that have no
     * thread destination (notifications like {@code runner_ready}, ring metadata, server status).
     * Filters MUST recognise such frames (e.g. via {@code params.threadId == null}) and pass
     * them through unless the caller deliberately wants to suppress them — a filter that
     * rejects broadcast frames will starve all subscribers of cross-cutting state.
     *
     * <p>The predicate runs on the dispatcher thread for each frame. It must be non-blocking and
     * effectively pure: a slow or throwing predicate stalls / kills a subscriber's dispatch
     * loop. Filtering decisions that need to do I/O belong in the listener, not the predicate.
     *
     * @param cursor   where in the frame stream to begin
     * @param filter   accepts frames to dispatch; rejects to silently drop
     * @param listener invoked on a dedicated virtual thread per subscriber; may block
     * @return a {@link Disposable} whose {@code dispose()} is idempotent
     */
    Disposable subscribe(Cursor cursor, Predicate<JsonNode> filter, Consumer<JsonNode> listener);

    /** Wall-clock of the last frame in either direction. */
    Instant lastActivityAt();

    Duration idleFor();

    /**
     * Stops the underlying container with {@code docker stop --time=graceTimeout} (SIGTERM →
     * grace → SIGKILL), then removes it. Idempotent. The hard SIGKILL mitigates the Pi SDK
     * abort-hang risk.
     */
    void close(Duration graceTimeout);

    /**
     * {@link AutoCloseable} contract: uses a hardcoded 30-second grace timeout. <b>This is NOT</b>
     * the registry's configured {@code hephaestus.mentor.grace-timeout-seconds} — callers who
     * want the configured default must call {@link #close(Duration)} explicitly.
     */
    @Override
    default void close() {
        close(Duration.ofSeconds(30));
    }
}

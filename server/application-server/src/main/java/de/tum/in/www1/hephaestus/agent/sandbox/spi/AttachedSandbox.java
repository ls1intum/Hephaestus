package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
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
     * @param cursor   where in the frame stream to begin
     * @param listener invoked on a dedicated virtual thread per subscriber; may block
     * @return a {@link Disposable} whose {@code dispose()} is idempotent
     */
    Disposable subscribe(Cursor cursor, Consumer<JsonNode> listener);

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

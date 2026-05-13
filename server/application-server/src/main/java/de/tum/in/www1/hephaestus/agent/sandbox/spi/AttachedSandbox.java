package de.tum.in.www1.hephaestus.agent.sandbox.spi;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import reactor.core.Disposable;

/**
 * Handle to a single attached sandbox session.
 *
 * <p>Returned by {@link InteractiveSandboxService#attach}. Encapsulates a live JSONL stdin/stdout
 * channel to a runner process inside a Docker container, plus the multi-subscriber fan-out and
 * idle bookkeeping that the registry uses.
 *
 * <h2>Frame fan-out semantics</h2>
 *
 * <p>{@link #subscribe} returns a {@link Disposable}. The implementation delivers, in order:
 *
 * <ol>
 *   <li>A snapshot of frames currently buffered in the session ring buffer (replay since the last
 *       drop, no duplicates, no gaps).
 *   <li>All subsequent live frames, in arrival order.
 * </ol>
 *
 * <p>Each subscriber has its own bounded queue drained on a dedicated virtual thread. A slow
 * listener drops frames from <em>its own</em> queue (counted via
 * {@code mentor_subscriber_dropped_total}) but does not affect the pump or other subscribers. A
 * listener that throws is logged once at WARN and downgraded to TRACE for subsequent throws;
 * subsequent frames are still delivered.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>The session is alive from {@code attach()} until {@link #close} is called, the runner exits,
 * the stdin pipe breaks, or the idle reaper evicts it. After termination, {@code subscribe()}
 * returns an already-disposed {@link Disposable} and {@code send()} throws
 * {@link InteractiveSandboxException}.
 *
 * <p>{@link AutoCloseable#close()} is provided as a convenience that delegates to
 * {@link #close(Duration)} with the registry's configured default grace timeout.
 */
public interface AttachedSandbox extends AutoCloseable {
    /** Unique session identifier (also used as container label / network name suffix). */
    UUID sessionId();

    /** User identifier — one half of the {@code (userId, workspaceId)} registry key. */
    String userId();

    /** Workspace identifier — second half of the registry key. */
    String workspaceId();

    /**
     * Send a JSON frame to the runner's stdin.
     *
     * <p>Blocks the calling thread until the frame has been fully written to stdin, or
     * {@code hephaestus.mentor.stdin-write-timeout-ms} elapses, or the session has terminated.
     *
     * <p>Implementation queues the frame on a bounded per-session writer queue
     * ({@code hephaestus.mentor.send-queue-capacity}). When the queue is full, {@code send()}
     * throws {@link InteractiveSandboxException} immediately (no further queuing) — this is the
     * primary backpressure signal to upstream HTTP callers.
     *
     * @param frame the JSON object to encode; must serialise to a single JSONL line
     * @throws InteractiveSandboxException if the session is closed, the stdin pipe is broken, the
     *     writer queue is full, the write times out, or the frame cannot be serialised
     */
    void send(JsonNode frame) throws InteractiveSandboxException;

    /**
     * Subscribe to stdout frames.
     *
     * @param listener invoked sequentially per-subscriber on a dedicated virtual thread; may
     *     block, but a listener slower than the producer will drop frames from its own queue
     * @return a {@link Disposable} whose {@code dispose()} removes the listener and stops the
     *     dispatcher thread. Idempotent — safe to call multiple times, safe after session close.
     */
    Disposable subscribe(Consumer<JsonNode> listener);

    /** Wall-clock instant of the last frame in either direction. Used by the idle reaper. */
    Instant lastActivityAt();

    /** {@code Duration.between(lastActivityAt(), Instant.now())}. */
    Duration idleFor();

    /**
     * Close the session.
     *
     * <p>Stops the underlying container with {@code docker stop --time=graceTimeout}: the daemon
     * sends SIGTERM to the container's PID 1 (which signals the runner via its process tree),
     * waits {@code graceTimeout}, then sends SIGKILL. The container is force-removed afterward.
     * The hard SIGKILL after grace mitigates the Pi SDK abort-hang risk
     * (pi-mono #2381 / #2677 / #2119).
     *
     * <p>Idempotent — safe to call concurrently with the reaper, with {@code send()}, and after
     * natural exit.
     *
     * @param graceTimeout SIGTERM → SIGKILL grace period
     */
    void close(Duration graceTimeout);

    /** Convenience for try-with-resources. Delegates to {@link #close(Duration)} with default grace. */
    @Override
    default void close() {
        close(Duration.ofSeconds(30));
    }
}

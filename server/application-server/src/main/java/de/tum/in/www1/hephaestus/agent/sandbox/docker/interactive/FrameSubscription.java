package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * One subscriber's state: a bounded queue of frames + a dedicated virtual-thread dispatcher.
 *
 * <p>Decouples the per-session stdout pump from arbitrary listener latency. Slow listeners drop
 * their own frames (counted via {@code mentor_subscriber_dropped_total}); a listener that throws
 * is logged once at WARN and downgraded thereafter, but never propagates to the pump.
 *
 * <p>The returned {@link Disposable#dispose()} is idempotent and CAS-guarded: the first call
 * flips the disposed flag, enqueues a sentinel to wake the dispatcher, removes the subscription
 * from the parent sandbox, and waits at most {@code DISPATCHER_JOIN_TIMEOUT_MS} for the
 * dispatcher thread to drain and exit. Subsequent calls are no-ops.
 */
final class FrameSubscription implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(FrameSubscription.class);

    /** Sentinel object enqueued on dispose to wake a blocked {@code queue.take()}. */
    private static final JsonNode TERMINAL_SENTINEL = com.fasterxml.jackson.databind.node.NullNode.getInstance();

    private static final long DISPATCHER_JOIN_TIMEOUT_MS = 250L;

    private final UUID subscriptionId = UUID.randomUUID();
    private final Consumer<JsonNode> listener;
    private final BlockingQueue<JsonNode> queue;
    private final Counter droppedCounter;
    private final Counter errorCounter;
    private final Runnable onDispose;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean errorLoggedAtWarn = new AtomicBoolean(false);
    private volatile Thread dispatcherThread;

    FrameSubscription(
        Consumer<JsonNode> listener,
        int queueCapacity,
        Counter droppedCounter,
        Counter errorCounter,
        Runnable onDispose
    ) {
        this.listener = listener;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.droppedCounter = droppedCounter;
        this.errorCounter = errorCounter;
        this.onDispose = onDispose;
    }

    /** Spawn the dispatcher virtual thread. Must be called exactly once after construction. */
    void start() {
        Thread t = Thread.ofVirtual()
            .name("mentor-sub-" + subscriptionId)
            .uncaughtExceptionHandler((thread, ex) -> log.warn("Subscriber dispatcher died unexpectedly", ex))
            .start(this::dispatchLoop);
        this.dispatcherThread = t;
    }

    /**
     * Offer a frame for delivery.
     *
     * <p>If the queue is full, the head (oldest) frame is dropped to make room — preserves the
     * "what just happened" window. Increments the dropped counter on overflow.
     *
     * <p>No-op when disposed.
     */
    void offer(JsonNode frame) {
        if (disposed.get()) {
            return;
        }
        if (!queue.offer(frame)) {
            // Drop oldest to keep recent — same policy as the session ring buffer
            queue.poll();
            droppedCounter.increment();
            queue.offer(frame);
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }

    @Override
    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        // Wake a blocked take() — the sentinel is identity-checked, not data-checked
        queue.offer(TERMINAL_SENTINEL);
        try {
            onDispose.run();
        } catch (Exception e) {
            log.debug("onDispose callback failed for subscription {}: {}", subscriptionId, e.toString());
        }
        Thread t = dispatcherThread;
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(DISPATCHER_JOIN_TIMEOUT_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void dispatchLoop() {
        while (!disposed.get()) {
            JsonNode frame;
            try {
                frame = queue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            if (frame == TERMINAL_SENTINEL) {
                return;
            }
            try {
                listener.accept(frame);
            } catch (Throwable t) {
                errorCounter.increment();
                if (errorLoggedAtWarn.compareAndSet(false, true)) {
                    log.warn("Mentor subscriber listener threw — further errors at TRACE", t);
                } else if (log.isTraceEnabled()) {
                    log.trace("Mentor subscriber listener threw", t);
                }
            }
        }
    }
}

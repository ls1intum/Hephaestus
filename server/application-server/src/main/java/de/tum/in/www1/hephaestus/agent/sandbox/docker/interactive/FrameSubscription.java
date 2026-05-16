package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.Counter;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;

/**
 * Per-subscriber bounded queue + virtual-thread dispatcher. Slow listeners drop their own frames
 * (drop-oldest); a throwing listener is logged once and downgraded — the pump is never affected.
 */
final class FrameSubscription implements Disposable {

    private static final Logger log = LoggerFactory.getLogger(FrameSubscription.class);

    // Identity-checked sentinel to wake a blocked queue.take() on dispose.
    private static final JsonNode TERMINAL_SENTINEL = com.fasterxml.jackson.databind.node.NullNode.getInstance();

    private static final long DISPATCHER_JOIN_TIMEOUT_MS = 250L;

    private final UUID subscriptionId = UUID.randomUUID();
    private final Consumer<JsonNode> listener;
    private final Predicate<JsonNode> filter;
    private final BlockingQueue<JsonNode> queue;
    private final Counter droppedCounter;
    private final Counter errorCounter;
    private final Runnable onDispose;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean errorLoggedAtWarn = new AtomicBoolean(false);
    private volatile Thread dispatcherThread;

    FrameSubscription(
        Consumer<JsonNode> listener,
        Predicate<JsonNode> filter,
        int queueCapacity,
        Counter droppedCounter,
        Counter errorCounter,
        Runnable onDispose
    ) {
        this.listener = listener;
        this.filter = filter;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.droppedCounter = droppedCounter;
        this.errorCounter = errorCounter;
        this.onDispose = onDispose;
    }

    void start() {
        Thread t = Thread.ofVirtual()
            .name("mentor-sub-" + subscriptionId)
            .uncaughtExceptionHandler((thread, ex) -> log.warn("Subscriber dispatcher died unexpectedly", ex))
            .start(this::dispatchLoop);
        this.dispatcherThread = t;
    }

    /**
     * Drop-oldest on overflow. No-op when disposed or when the filter rejects the frame.
     *
     * <p>The filter runs HERE (before enqueue): rejected frames don't consume queue capacity
     * and do not fire the drop counter. A defensively-thrown filter degrades to "frame
     * suppressed" + counted as an error — protects the pump from a buggy predicate.
     */
    void offer(JsonNode frame) {
        if (disposed.get()) {
            return;
        }
        try {
            if (!filter.test(frame)) {
                return;
            }
        } catch (Throwable t) {
            errorCounter.increment();
            if (errorLoggedAtWarn.compareAndSet(false, true)) {
                log.warn("Subscriber filter threw — frame suppressed; further filter errors at TRACE", t);
            } else if (log.isTraceEnabled()) {
                log.trace("Subscriber filter threw", t);
            }
            return;
        }
        if (!queue.offer(frame)) {
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

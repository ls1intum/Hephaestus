package de.tum.cit.aet.hephaestus.integration.core.sync.push;

import de.tum.cit.aet.hephaestus.core.runtime.ConditionalOnServerRole;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Per-workspace registry of active {@link SseEmitter}s for the sync-observability live-push stream
 * (design doc §3.5). Owns everything transport-level: subscribe/deregister lifecycle, per-emitter
 * bounded queueing with drop-oldest backpressure, single-writer serialisation per emitter (mirrors
 * {@code MentorSseChannel}'s {@link ReentrantLock} discipline — a virtual thread parked on a lock
 * does not pin its carrier, unlike {@code synchronized}), the {@code :ping} heartbeat, and
 * trailing-edge coalescing per {@code (workspaceId, connectionId, scope)}.
 *
 * <p>A slow or broken client must never stall another workspace's (or another emitter's) delivery:
 * every send — heartbeat or hint — runs on its own virtual-thread task, guarded only by that one
 * emitter's lock.
 */
@Component
@ConditionalOnServerRole
public class SyncEventHub {

    private static final Logger log = LoggerFactory.getLogger(SyncEventHub.class);

    private static final long EMITTER_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private static final int QUEUE_CAPACITY = 64;
    private static final String EVENT_NAME = "sync";
    private static final long HEARTBEAT_INTERVAL_MS = 20_000L;
    private static final Duration DEFAULT_COALESCE_WINDOW = Duration.ofSeconds(1);

    private final ObjectMapper objectMapper;
    private final Duration coalesceWindow;
    private final Supplier<SseEmitter> emitterFactory;

    /** Live subscribers, per workspace. Set is identity-based (no equals/hashCode override on {@link Subscriber}). */
    private final Map<Long, Set<Subscriber>> subscribersByWorkspace = new ConcurrentHashMap<>();

    /** Single writer per emitter; fine to share across all emitters — virtual threads are cheap. */
    private final ExecutorService writerExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Trailing-edge coalescing: last hint per key, flushed once the window elapses. */
    private final Map<CoalesceKey, SyncEventHint> pendingHints = new ConcurrentHashMap<>();
    private final Set<CoalesceKey> pendingFlushes = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService coalesceScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sync-event-coalesce");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Explicitly {@code @Autowired}: Spring only auto-selects an unannotated constructor when
     * exactly one exists on the class, and this class also carries package-private test-seam
     * constructors (below) for a short coalesce window / a recording emitter factory.
     */
    @Autowired
    public SyncEventHub(ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_COALESCE_WINDOW, () -> new SseEmitter(EMITTER_TIMEOUT_MS));
    }

    /** Test seam: a short coalesce window keeps unit tests fast. */
    SyncEventHub(ObjectMapper objectMapper, Duration coalesceWindow) {
        this(objectMapper, coalesceWindow, () -> new SseEmitter(EMITTER_TIMEOUT_MS));
    }

    /** Test seam: a custom emitter factory lets tests substitute a recording {@link SseEmitter}. */
    SyncEventHub(ObjectMapper objectMapper, Duration coalesceWindow, Supplier<SseEmitter> emitterFactory) {
        this.objectMapper = objectMapper;
        this.coalesceWindow = coalesceWindow;
        this.emitterFactory = emitterFactory;
    }

    /**
     * Register a fresh emitter for this workspace. Deregisters itself on completion, timeout, or
     * transport error — callers just return the emitter to Spring MVC.
     */
    public SseEmitter subscribe(long workspaceId) {
        SseEmitter emitter = emitterFactory.get();
        Subscriber subscriber = new Subscriber(workspaceId, emitter);
        subscribersByWorkspace.computeIfAbsent(workspaceId, key -> ConcurrentHashMap.newKeySet()).add(subscriber);

        emitter.onCompletion(() -> deregister(subscriber));
        emitter.onTimeout(() -> {
            deregister(subscriber);
            safeComplete(emitter);
        });
        emitter.onError(throwable -> deregister(subscriber));
        return emitter;
    }

    /**
     * Publish a hint for a workspace. Coalesced: within {@link #coalesceWindow}, only the last hint
     * per {@code (workspaceId, connectionId, scope)} is actually delivered (trailing edge — the
     * window is scheduled once and re-armed hints replace the pending payload in place, so a
     * terminal transition is never swallowed by a leading-edge drop).
     */
    public void publish(long workspaceId, SyncEventHint hint) {
        CoalesceKey key = new CoalesceKey(workspaceId, hint.connectionId(), hint.scope());
        pendingHints.put(key, hint);
        if (pendingFlushes.add(key)) {
            scheduleFlush(key);
        }
    }

    private void scheduleFlush(CoalesceKey key) {
        try {
            coalesceScheduler.schedule(() -> flush(key), coalesceWindow.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // Shutting down: flush immediately in-line rather than losing the hint entirely.
            flush(key);
        }
    }

    private void flush(CoalesceKey key) {
        pendingFlushes.remove(key);
        SyncEventHint hint = pendingHints.remove(key);
        if (hint != null) {
            fanOut(key.workspaceId(), hint);
        }
    }

    private void fanOut(long workspaceId, SyncEventHint hint) {
        Set<Subscriber> subscribers = subscribersByWorkspace.get(workspaceId);
        if (subscribers == null || subscribers.isEmpty()) {
            return;
        }
        for (Subscriber subscriber : subscribers) {
            enqueueHint(subscriber, hint);
        }
    }

    private void enqueueHint(Subscriber subscriber, SyncEventHint hint) {
        if (subscriber.closed.get()) {
            return;
        }
        synchronized (subscriber.queue) {
            if (subscriber.queue.size() >= QUEUE_CAPACITY) {
                subscriber.queue.pollFirst(); // drop-oldest
            }
            subscriber.queue.addLast(hint);
        }
        scheduleDrain(subscriber);
    }

    private void scheduleDrain(Subscriber subscriber) {
        if (subscriber.draining.compareAndSet(false, true)) {
            try {
                writerExecutor.execute(() -> drain(subscriber));
            } catch (RejectedExecutionException e) {
                subscriber.draining.set(false);
            }
        }
    }

    private void drain(Subscriber subscriber) {
        subscriber.writeLock.lock();
        try {
            while (true) {
                SyncEventHint hint;
                synchronized (subscriber.queue) {
                    hint = subscriber.queue.pollFirst();
                }
                if (hint == null) {
                    break;
                }
                if (!sendHint(subscriber, hint)) {
                    return; // failure already deregistered the subscriber
                }
            }
        } finally {
            subscriber.writeLock.unlock();
            subscriber.draining.set(false);
            boolean more;
            synchronized (subscriber.queue) {
                more = !subscriber.queue.isEmpty();
            }
            if (more && !subscriber.closed.get()) {
                scheduleDrain(subscriber);
            }
        }
    }

    /** Must be called while holding {@code subscriber.writeLock}. */
    private boolean sendHint(Subscriber subscriber, SyncEventHint hint) {
        try {
            String json = objectMapper.writeValueAsString(hint);
            subscriber.emitter.send(SseEmitter.event().name(EVENT_NAME).data(json));
            return true;
        } catch (Exception e) {
            log.debug("Sync SSE send failed; deregistering: {}", e.toString());
            deregister(subscriber);
            safeComplete(subscriber.emitter);
            return false;
        }
    }

    /**
     * Comment-only keep-alive so proxies (Traefik idles at 300s) never kill an otherwise-quiet
     * stream. Package-private so unit tests can drive it directly without a live scheduler.
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS, initialDelay = HEARTBEAT_INTERVAL_MS)
    void sendHeartbeats() {
        for (Set<Subscriber> subscribers : subscribersByWorkspace.values()) {
            for (Subscriber subscriber : subscribers) {
                if (subscriber.closed.get()) {
                    continue;
                }
                try {
                    writerExecutor.execute(() -> sendPing(subscriber));
                } catch (RejectedExecutionException ignored) {
                    // Shutting down.
                }
            }
        }
    }

    private void sendPing(Subscriber subscriber) {
        if (subscriber.closed.get()) {
            return;
        }
        subscriber.writeLock.lock();
        try {
            if (subscriber.closed.get()) {
                return;
            }
            subscriber.emitter.send(SseEmitter.event().comment("ping"));
        } catch (Exception e) {
            log.debug("Sync SSE heartbeat failed; deregistering: {}", e.toString());
            deregister(subscriber);
            safeComplete(subscriber.emitter);
        } finally {
            subscriber.writeLock.unlock();
        }
    }

    private void deregister(Subscriber subscriber) {
        if (!subscriber.closed.compareAndSet(false, true)) {
            return;
        }
        Set<Subscriber> subscribers = subscribersByWorkspace.get(subscriber.workspaceId);
        if (subscribers != null) {
            subscribers.remove(subscriber);
            // Reference-identity conditional remove: only drop the map entry if nothing else
            // (a concurrent subscribe) repopulated this exact set between the two lines above.
            if (subscribers.isEmpty()) {
                subscribersByWorkspace.remove(subscriber.workspaceId, subscribers);
            }
        }
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (RuntimeException ignored) {
            // Already completing/completed.
        }
    }

    @PreDestroy
    void shutdown() {
        coalesceScheduler.shutdownNow();
        writerExecutor.shutdown();
        try {
            if (!writerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                writerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Test seams (package-private — used by unit tests in the same package).

    int subscriberCount(long workspaceId) {
        Set<Subscriber> subscribers = subscribersByWorkspace.get(workspaceId);
        return subscribers == null ? 0 : subscribers.size();
    }

    private record CoalesceKey(long workspaceId, Long connectionId, String scope) {}

    private static final class Subscriber {

        final long workspaceId;
        final SseEmitter emitter;
        final Deque<SyncEventHint> queue = new ArrayDeque<>();
        final ReentrantLock writeLock = new ReentrantLock();
        final AtomicBoolean draining = new AtomicBoolean(false);
        final AtomicBoolean closed = new AtomicBoolean(false);

        Subscriber(long workspaceId, SseEmitter emitter) {
            this.workspaceId = workspaceId;
            this.emitter = emitter;
        }
    }
}

package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import de.tum.in.www1.hephaestus.agent.sandbox.spi.EvictionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Micrometer instruments for the interactive sandbox. Stable names — do not rename without
 * dashboard coordination. Bounded cardinality: no per-session/user/workspace labels.
 */
public final class InteractiveSandboxMetrics {

    final Counter attachFailureImage;
    final Counter attachFailureStart;
    final Counter attachFailureStdin;
    final Counter attachFailureFirstFrameTimeout;
    final Counter attachFailureFirstFrameFailed;
    final Counter attachFailureMaxSessions;
    final Counter attachFailureOther;

    final Timer attachDuration;

    final Counter sendBytes;
    final Counter recvBytes;

    final Counter sendRejectedQueueFull;
    final Counter sendRejectedWriteTimeout;
    final Counter sendRejectedBrokenPipe;
    final Counter sendRejectedClosed;

    final Counter ringBufferDropped;

    final Counter subscriberDropped;
    final Counter subscriberError;

    final Counter frameParseError;

    final Timer lifetime;
    final DistributionSummary subscribersAtClose;

    private final Map<EvictionReason, Counter> evictionsByReason;

    /**
     * Parallel counter to {@link #evictionsByReason} under the SPI-aligned metric name. Kept
     * pre-registered per reason so the hot path (eviction at close) is a single map lookup +
     * counter increment with no allocation. The {@code mentor.session.eviction} variant is
     * retained for dashboard back-compat — both increment together on every eviction.
     */
    private final Map<EvictionReason, Counter> interactiveEvictedByReason;

    /**
     * Per-user counter for {@code interactive_sandbox.frame_ring.dropped_total}. The cardinality
     * cap (50 distinct userIds across the JVM) is enforced upstream by a {@code MeterFilter} —
     * any further userId triggers a {@code denyAll} for THIS specific counter only, so the rest
     * of the registry is unaffected.
     */
    private final ConcurrentHashMap<String, Counter> userDroppedCounters = new ConcurrentHashMap<>();

    /**
     * Debounce table: max one increment per second per {@code (userId, sandboxId)} pair. The
     * buffer drop itself is unconditional — only the counter increment is throttled. Entries
     * are evicted when {@link #evictDropDebounce(String, UUID)} is called from the per-sandbox
     * close path; the 50-userId cardinality cap bounds the absolute size in the meantime.
     */
    private final ConcurrentHashMap<String, AtomicLong> dropDebounce = new ConcurrentHashMap<>();

    private final MeterRegistry registry;
    private final Clock clock;
    private static final long DROP_DEBOUNCE_INTERVAL_MS = 1_000L;

    public InteractiveSandboxMetrics(MeterRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    /** Test seam — production wiring goes through {@link #InteractiveSandboxMetrics(MeterRegistry)}. */
    InteractiveSandboxMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
        this.attachFailureImage = attachFailure(registry, "image_pull_failed");
        this.attachFailureStart = attachFailure(registry, "container_start_failed");
        this.attachFailureStdin = attachFailure(registry, "stdin_open_failed");
        // _timeout = runner went silent; _failed = pump/writer terminated before any frame.
        this.attachFailureFirstFrameTimeout = attachFailure(registry, "first_frame_timeout");
        this.attachFailureFirstFrameFailed = attachFailure(registry, "first_frame_failed");
        this.attachFailureMaxSessions = attachFailure(registry, "max_sessions");
        this.attachFailureOther = attachFailure(registry, "other");

        this.attachDuration = Timer.builder("mentor.attach.duration")
            .description("Time from container spawn to first JSONL frame received from runner")
            .register(registry);

        this.sendBytes = Counter.builder("mentor.send.frame.bytes")
            .tag("direction", "in")
            .description("Bytes sent to runner stdin (UTF-8 encoded JSONL including the newline terminator)")
            .register(registry);
        this.recvBytes = Counter.builder("mentor.send.frame.bytes")
            .tag("direction", "out")
            .description("Bytes received from runner stdout (UTF-8 encoded JSONL line, no terminator)")
            .register(registry);

        this.sendRejectedQueueFull = sendRejected(registry, "queue_full");
        this.sendRejectedWriteTimeout = sendRejected(registry, "write_timeout");
        this.sendRejectedBrokenPipe = sendRejected(registry, "broken_pipe");
        this.sendRejectedClosed = sendRejected(registry, "closed");

        this.ringBufferDropped = Counter.builder("mentor.ring.buffer.dropped")
            .description("Frames evicted from the per-session ring buffer (drop-oldest on overflow)")
            .register(registry);

        this.subscriberDropped = Counter.builder("mentor.subscriber.dropped")
            .tag("reason", "queue_full")
            .description("Frames dropped from a subscriber's bounded queue due to slow listener")
            .register(registry);
        this.subscriberError = Counter.builder("mentor.subscriber.error")
            .description("Subscriber listener invocations that threw")
            .register(registry);

        this.frameParseError = Counter.builder("mentor.frame.parse.error")
            .description("JSONL frames that failed to parse (skipped, session continues)")
            .register(registry);

        // Per-session counters / timers / gauges share the mentor.session.* stem.
        this.lifetime = Timer.builder("mentor.session.lifetime")
            .description("End-to-end session lifetime (attach to close)")
            .register(registry);

        this.subscribersAtClose = DistributionSummary.builder("mentor.session.subscribers.at.close")
            .description("Subscriber count observed at session close")
            .register(registry);

        this.evictionsByReason = new EnumMap<>(EvictionReason.class);
        this.interactiveEvictedByReason = new EnumMap<>(EvictionReason.class);
        for (EvictionReason r : EvictionReason.values()) {
            evictionsByReason.put(
                r,
                Counter.builder("mentor.session.eviction")
                    .tag("reason", r.tag())
                    .description("Session evictions by reason (covers all termination causes)")
                    .register(registry)
            );
            interactiveEvictedByReason.put(
                r,
                Counter.builder("interactive_sandbox.evicted")
                    .tag("reason", r.tag())
                    .description("Interactive sandbox evictions by reason (SPI-aligned metric name)")
                    .register(registry)
            );
        }
    }

    Counter evictionsBy(EvictionReason reason) {
        return evictionsByReason.get(reason);
    }

    /**
     * Pre-registered counter for {@code interactive_sandbox.evicted_total{reason}}. Both this
     * and {@link #evictionsBy(EvictionReason)} are incremented from the same call site so the
     * two metric streams agree.
     */
    Counter interactiveEvictedBy(EvictionReason reason) {
        return interactiveEvictedByReason.get(reason);
    }

    /**
     * Record a ring-buffer drop for {@code (userId, sandboxId)}. The caller is responsible for
     * the actual buffer eviction; this only manages the metric increment. Two effects:
     *
     * <ol>
     *   <li>Global counter {@link #ringBufferDropped} is always incremented (cheap, untagged).
     *   <li>Per-user counter {@code interactive_sandbox.frame_ring.dropped_total{userId}} is
     *       incremented at most once per {@value #DROP_DEBOUNCE_INTERVAL_MS}ms per
     *       {@code (userId, sandboxId)} pair. Under steady-state overflow the per-second cap
     *       gives dashboards a stable rate without amplifying registry pressure.
     * </ol>
     */
    void recordRingBufferDrop(String userId, UUID sandboxId) {
        ringBufferDropped.increment();
        if (userId == null || sandboxId == null) {
            return;
        }
        long now = clock.millis();
        String key = userId + ':' + sandboxId;
        AtomicLong lastEmit = dropDebounce.computeIfAbsent(key, k -> new AtomicLong(0L));
        long prev = lastEmit.get();
        if (now - prev < DROP_DEBOUNCE_INTERVAL_MS) {
            return;
        }
        if (!lastEmit.compareAndSet(prev, now)) {
            // Another thread won the CAS within the same window — they recorded, we skip.
            return;
        }
        userDroppedCounter(userId).increment();
    }

    /**
     * Eviction hook called from the per-sandbox close path. The debounce map is bounded by the
     * 50-userId cardinality cap, but evicting per-sandbox keeps it tight under user churn.
     */
    void evictDropDebounce(String userId, UUID sandboxId) {
        if (userId == null || sandboxId == null) return;
        dropDebounce.remove(userId + ':' + sandboxId);
    }

    private Counter userDroppedCounter(String userId) {
        return userDroppedCounters.computeIfAbsent(userId, u ->
            Counter.builder("interactive_sandbox.frame_ring.dropped")
                .tag("userId", u)
                .description("Per-user frame ring overflow drops (debounced to <=1/s per session, capped at 50 distinct users)")
                .register(registry)
        );
    }

    private static Counter attachFailure(MeterRegistry registry, String reason) {
        return Counter.builder("mentor.attach.failure")
            .tag("reason", reason)
            .description("attach() failures by reason")
            .register(registry);
    }

    private static Counter sendRejected(MeterRegistry registry, String reason) {
        return Counter.builder("mentor.send.rejected")
            .tag("reason", reason)
            .description("send() rejections by reason")
            .register(registry);
    }
}

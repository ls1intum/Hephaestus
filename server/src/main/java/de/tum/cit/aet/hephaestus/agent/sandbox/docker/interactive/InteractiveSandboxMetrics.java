package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.EvictionReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.EnumMap;
import java.util.Map;

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

    public InteractiveSandboxMetrics(MeterRegistry registry) {
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
        for (EvictionReason r : EvictionReason.values()) {
            evictionsByReason.put(
                r,
                Counter.builder("mentor.session.eviction")
                    .tag("reason", r.tag())
                    .description("Session evictions by reason (covers all termination causes)")
                    .register(registry)
            );
        }
    }

    Counter evictionsBy(EvictionReason reason) {
        return evictionsByReason.get(reason);
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

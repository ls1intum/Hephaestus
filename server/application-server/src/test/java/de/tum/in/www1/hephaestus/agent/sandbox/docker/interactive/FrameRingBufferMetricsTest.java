package de.tum.in.www1.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.IntNode;
import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Behaviour of the per-user ring-buffer drop counter:
 *
 * <ul>
 *   <li>Buffer drops are unconditional — every overflow records to the global counter.
 *   <li>The per-user tagged counter is debounced to at most one increment per second per
 *       {@code (userId, sandboxId)} pair.
 * </ul>
 */
@DisplayName("FrameRingBuffer: per-user drop metric")
class FrameRingBufferMetricsTest extends BaseUnitTest {

    @Test
    @DisplayName("100 drops within 1ms produce exactly 1 per-user increment but 100 global increments")
    void debouncesWithinOneSecondWindow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong fakeMillis = new AtomicLong(1_000_000L);
        Clock fixed = clockOf(fakeMillis);
        InteractiveSandboxMetrics metrics = new InteractiveSandboxMetrics(registry, fixed);

        String userId = "u1";
        UUID sandboxId = UUID.randomUUID();
        FrameRingBuffer ring = new FrameRingBuffer(8, () -> metrics.recordRingBufferDrop(userId, sandboxId));

        // Fill to capacity, then drop 100 frames in the same 1ms tick.
        for (int i = 0; i < 8; i++) ring.offer(IntNode.valueOf(i));
        for (int i = 0; i < 100; i++) ring.offer(IntNode.valueOf(i));

        assertThat(metrics.ringBufferDropped.count()).isEqualTo(100.0);
        Counter perUser = registry
            .find("interactive_sandbox.frame_ring.dropped")
            .tag("userId", userId)
            .counter();
        assertThat(perUser).isNotNull();
        // First call sets the timestamp; subsequent calls within 1s window are dropped.
        assertThat(perUser.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("drops across multiple 1s windows produce one increment per window")
    void emitOncePerSecondWindow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong fakeMillis = new AtomicLong(2_000_000L);
        Clock fixed = clockOf(fakeMillis);
        InteractiveSandboxMetrics metrics = new InteractiveSandboxMetrics(registry, fixed);

        String userId = "u2";
        UUID sandboxId = UUID.randomUUID();
        FrameRingBuffer ring = new FrameRingBuffer(4, () -> metrics.recordRingBufferDrop(userId, sandboxId));

        for (int i = 0; i < 4; i++) ring.offer(IntNode.valueOf(i));

        long durationMillis = 5_500L; // 5.5 seconds of dropping
        long start = fakeMillis.get();
        long end = start + durationMillis;
        int totalDrops = 0;
        while (fakeMillis.get() < end) {
            ring.offer(IntNode.valueOf(99));
            totalDrops++;
            fakeMillis.addAndGet(50L); // advance 50ms between drops
        }
        assertThat(metrics.ringBufferDropped.count()).isEqualTo((double) totalDrops);

        Counter perUser = registry
            .find("interactive_sandbox.frame_ring.dropped")
            .tag("userId", userId)
            .counter();
        assertThat(perUser).isNotNull();
        // Spec contract: at most ceil(durationMillis / 1000) + 1 increments.
        double upperBound = Math.ceil(durationMillis / 1000.0) + 1.0;
        assertThat(perUser.count()).isLessThanOrEqualTo(upperBound);
        // And at least one — first eviction must record.
        assertThat(perUser.count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("distinct users produce distinct tagged counters")
    void distinctUsersTracked() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        // Start time must be > DROP_DEBOUNCE_INTERVAL_MS (1000 ms): each (userId, sandbox) pair
        // has its own initial last-emit of 0, so `now - 0` must clear the window for the first
        // call to record. A 0-millis clock would silently swallow the first emit on every key.
        AtomicLong fakeMillis = new AtomicLong(10_000L);
        InteractiveSandboxMetrics metrics = new InteractiveSandboxMetrics(registry, clockOf(fakeMillis));

        UUID sandbox = UUID.randomUUID();
        metrics.recordRingBufferDrop("alice", sandbox);
        metrics.recordRingBufferDrop("bob", sandbox);

        assertThat(registry.find("interactive_sandbox.frame_ring.dropped").tag("userId", "alice").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.find("interactive_sandbox.frame_ring.dropped").tag("userId", "bob").counter().count())
            .isEqualTo(1.0);
    }

    @Test
    @DisplayName("evictDropDebounce removes the per-pair tracking entry — next drop emits again immediately")
    void evictResetsDebounceWindow() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AtomicLong fakeMillis = new AtomicLong(1_000_000L);
        InteractiveSandboxMetrics metrics = new InteractiveSandboxMetrics(registry, clockOf(fakeMillis));

        String userId = "u3";
        UUID sandboxId = UUID.randomUUID();
        metrics.recordRingBufferDrop(userId, sandboxId);
        // Second call inside the same window is suppressed.
        metrics.recordRingBufferDrop(userId, sandboxId);
        Counter c = registry.find("interactive_sandbox.frame_ring.dropped").tag("userId", userId).counter();
        assertThat(c.count()).isEqualTo(1.0);

        metrics.evictDropDebounce(userId, sandboxId);
        // After eviction the debounce timestamp is gone — next call emits even though no time passed.
        metrics.recordRingBufferDrop(userId, sandboxId);
        assertThat(c.count()).isEqualTo(2.0);
    }

    private static Clock clockOf(AtomicLong millisSource) {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneId.systemDefault();
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return Instant.ofEpochMilli(millisSource.get());
            }

            @Override
            public long millis() {
                return millisSource.get();
            }
        };
    }
}

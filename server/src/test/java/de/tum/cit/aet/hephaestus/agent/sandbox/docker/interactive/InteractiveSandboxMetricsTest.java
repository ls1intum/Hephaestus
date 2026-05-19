package de.tum.cit.aet.hephaestus.agent.sandbox.docker.interactive;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.sandbox.spi.EvictionReason;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Locks in the stable set of Micrometer instruments published by the interactive sandbox.
 * A regression that accidentally renames or drops a meter ID will fail this test before it
 * silently breaks Grafana dashboards.
 */
@DisplayName("InteractiveSandboxMetrics")
class InteractiveSandboxMetricsTest extends BaseUnitTest {

    @Test
    @DisplayName("publishes the documented stable set of meter IDs")
    void publishesStableMeterIds() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new InteractiveSandboxMetrics(reg);

        Set<String> meterNames = reg
            .getMeters()
            .stream()
            .map(Meter::getId)
            .map(io.micrometer.core.instrument.Meter.Id::getName)
            .collect(Collectors.toSet());

        // Note: `mentor.session.active` and `mentor.watchdog.targets` are gauges owned by
        // InteractiveSandboxRegistry and registered there, not by this metrics object.
        List<String> expected = List.of(
            "mentor.attach.duration",
            "mentor.attach.failure",
            "mentor.send.frame.bytes",
            "mentor.send.rejected",
            "mentor.ring.buffer.dropped",
            "mentor.subscriber.dropped",
            "mentor.subscriber.error",
            "mentor.frame.parse.error",
            "mentor.session.lifetime",
            "mentor.session.subscribers.at.close",
            "mentor.session.eviction"
        );
        assertThat(meterNames).containsAll(expected);
    }

    @Test
    @DisplayName("registers every attach-failure reason as a distinct tag on mentor.attach.failure")
    void attachFailureReasonsExposed() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new InteractiveSandboxMetrics(reg);

        Set<String> reasons = reg
            .find("mentor.attach.failure")
            .counters()
            .stream()
            .map(c -> c.getId().getTag("reason"))
            .collect(Collectors.toSet());

        assertThat(reasons).containsExactlyInAnyOrder(
            "image_pull_failed",
            "container_start_failed",
            "stdin_open_failed",
            "first_frame_timeout",
            "first_frame_failed",
            "max_sessions",
            "other"
        );
    }

    @Test
    @DisplayName("registers every send-rejected reason as a distinct tag on mentor.send.rejected")
    void sendRejectedReasonsExposed() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new InteractiveSandboxMetrics(reg);

        Set<String> reasons = reg
            .find("mentor.send.rejected")
            .counters()
            .stream()
            .map(c -> c.getId().getTag("reason"))
            .collect(Collectors.toSet());

        assertThat(reasons).containsExactlyInAnyOrder("queue_full", "write_timeout", "broken_pipe", "closed");
    }

    @Test
    @DisplayName("registers a mentor.session.eviction counter for every EvictionReason")
    void evictionReasonsExposed() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        InteractiveSandboxMetrics m = new InteractiveSandboxMetrics(reg);

        Set<String> reasons = reg
            .find("mentor.session.eviction")
            .counters()
            .stream()
            .map(c -> c.getId().getTag("reason"))
            .collect(Collectors.toSet());

        for (EvictionReason r : EvictionReason.values()) {
            assertThat(reasons).contains(r.tag());
            // Every reason must resolve to a non-null counter so the adapter can always increment.
            assertThat(m.evictionsBy(r)).isNotNull();
        }
    }

    @Test
    @DisplayName("registers mentor.send.frame.bytes with both in and out direction tags")
    void sendBytesHasBothDirections() {
        SimpleMeterRegistry reg = new SimpleMeterRegistry();
        new InteractiveSandboxMetrics(reg);

        Set<String> directions = reg
            .find("mentor.send.frame.bytes")
            .counters()
            .stream()
            .map(c -> c.getId().getTag("direction"))
            .collect(Collectors.toSet());

        assertThat(directions).containsExactlyInAnyOrder("in", "out");
    }
}

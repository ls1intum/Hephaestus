package de.tum.cit.aet.hephaestus.core.runtime.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

class WorkerControlChannelHealthIndicatorTest extends BaseUnitTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("connectionStates")
    void reflectsConnectionState(String label, boolean connected, Instant lastInbound, Status expected) {
        WorkerControlPublisher publisher = mock(WorkerControlPublisher.class);
        when(publisher.isConnected()).thenReturn(connected);
        when(publisher.lastInboundAt()).thenReturn(lastInbound);

        Health health = new WorkerControlChannelHealthIndicator(publisher, props(Duration.ofSeconds(20))).health();

        assertThat(health.getStatus()).isEqualTo(expected);
        assertThat(health.getDetails()).containsEntry("connected", connected);
    }

    static Stream<Arguments> connectionStates() {
        return Stream.of(
            Arguments.of("up when connected + recent inbound", true, Instant.now(), Status.UP),
            Arguments.of("down when disconnected", false, Instant.EPOCH, Status.DOWN)
        );
    }

    @Test
    void downWhenInboundStaleEvenIfConnected() {
        // Catches the silent-stall case where TCP is up but no frames are arriving.
        WorkerControlPublisher publisher = mock(WorkerControlPublisher.class);
        when(publisher.isConnected()).thenReturn(true);
        when(publisher.lastInboundAt()).thenReturn(Instant.now().minusSeconds(120));

        Health health = new WorkerControlChannelHealthIndicator(publisher, props(Duration.ofSeconds(20))).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }

    private static WorkerProperties props(Duration interval) {
        return new WorkerProperties(
            "w",
            new WorkerProperties.Capacity("1", "1"),
            new WorkerProperties.Drain(Duration.ofMinutes(5)),
            new WorkerProperties.Heartbeat(interval),
            new WorkerProperties.Control(URI.create("ws://example"), "tok", Duration.ofSeconds(10)),
            new WorkerProperties.Llm(null, null)
        );
    }
}
